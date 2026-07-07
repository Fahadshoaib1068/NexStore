package com.example.store.service;

import com.example.store.config.RabbitMQConfig;
import com.example.store.model.VideoUpload;
import com.example.store.repository.VideoRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.*;

@Service
public class VideoProcessingService {

    private final VideoRepository videoRepository;

    @Value("${video.processed.path}")
    private String processedPath;

    public VideoProcessingService(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    // ─── RABBITMQ CONSUMER
    @RabbitListener(queues = RabbitMQConfig.VIDEO_QUEUE, concurrency = "1")
    public void handleVideoProcessing(Integer videoId) {
        VideoUpload video = videoRepository.findById(videoId);
        if (video == null) {
            System.out.println("Video #" + videoId + " not found, skipping");
            return;
        }
        processVideo(video);
    }

    // ─── MAIN PROCESSING LOGIC ────────────────────────────────────
    private void processVideo(VideoUpload video) {
        Integer videoId   = video.getVideo_id();
        String  inputPath = video.getFile_path();

        System.out.println("Starting processing for video #" + videoId);
        // status is already PROCESSING — set atomically by markProcessing() in the controller

        try {
            // Create output directory for this video
            Path outputDir = Paths.get(processedPath, String.valueOf(videoId));
            Files.createDirectories(outputDir);

            // Process 3 qualities
            boolean success =
                    convertVideo(inputPath, outputDir, videoId, "360p",  640,  360) &&
                            convertVideo(inputPath, outputDir, videoId, "480p",  854,  480) &&
                            convertVideo(inputPath, outputDir, videoId, "720p", 1280,  720);

            if (success) {
                videoRepository.updateStatus(videoId, "COMPLETED");
                System.out.println("Video #" + videoId + " processing COMPLETED");
            } else {
                videoRepository.updateStatus(videoId, "FAILED");
                System.out.println("Video #" + videoId + " processing FAILED");
            }

        } catch (Exception e) {
            e.printStackTrace();
            videoRepository.updateStatus(videoId, "FAILED");
            System.out.println(" Video #" + videoId + " processing FAILED: " + e.getMessage());
        }
    }

    // ─── CONVERT VIDEO USING FFMPEG USING  DOCKER ────────────────────
    private boolean convertVideo(String inputPath, Path outputDir,
                                 Integer videoId, String quality,
                                 int width, int height) {
        try {
            String outputFileName = videoId + "_" + quality + ".mp4";
            Path   outputPath     = outputDir.resolve(outputFileName);

            System.out.println(" Converting to " + quality + " → " + outputPath);

            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "run", "--rm",
                    "-v", getVideosRootPath() + ":/videos",
                    "linuxserver/ffmpeg",
                    "-i", toDockerPath(inputPath),
                    "-vf", "scale=" + width + ":" + height,
                    "-c:v", "libx264",
                    "-crf", "23",
                    "-preset", "fast",
                    "-c:a", "aac",
                    "-y",
                    toDockerPath(outputPath.toString())
            );

            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[FFmpeg] " + line);
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                String relativeOutput = videoId + "/" + outputFileName;
                videoRepository.saveProcessed(videoId, quality, relativeOutput);
                System.out.println(" " + quality + " done — exit code: " + exitCode);
                return true;
            } else {
                System.out.println(" " + quality + " failed — exit code: " + exitCode);
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ─── HELPERS ────────────────────────────────────────────────────

    private String getVideosRootPath() {
        Path uploadsPath = Paths.get(processedPath).getParent();
        return uploadsPath.toString().replace("\\", "/");
    }

    private String toDockerPath(String windowsPath) {
        String normalized = windowsPath.replace("\\", "/");
        String videosRoot = getVideosRootPath();
        return normalized.replace(videosRoot, "/videos");
    }
}