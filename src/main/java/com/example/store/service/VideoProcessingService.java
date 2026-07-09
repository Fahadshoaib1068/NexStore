package com.example.store.service;

import com.example.store.model.VideoUpload;
import com.example.store.repository.VideoRepository;
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

    @Value("${video.thumbnail.path}")
    private String thumbnailPath;

    public VideoProcessingService(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    // ─── MANUAL TRIGGER ───────────────────────────────────────────
    public void processNow(VideoUpload video) {
        new Thread(() -> processVideo(video)).start();
    }

    // ─── MAIN PROCESSING ──────────────────────────────────────────
    private void processVideo(VideoUpload video) {
        Integer videoId   = video.getVideo_id();
        String  inputPath = video.getFile_path();

        System.out.println("Starting processing for video #" + videoId);
        videoRepository.updateStatus(videoId, "PROCESSING");

        try {
            // Create output directory for processed videos
            Path outputDir = Paths.get(processedPath, String.valueOf(videoId));
            Files.createDirectories(outputDir);

            // Create thumbnails directory
            Path thumbDir = Paths.get(thumbnailPath);
            Files.createDirectories(thumbDir);

            // Generate thumbnail first
            boolean thumbOk = generateThumbnail(inputPath, videoId);
            if (thumbOk) {
                System.out.println(" Thumbnail generated for video #" + videoId);
            } else {
                System.out.println("Thumbnail failed but continuing processing...");
            }

            // Convert to 3 qualities
            boolean success =
                    convertVideo(inputPath, outputDir, videoId, "360p",  640,  360) &&
                            convertVideo(inputPath, outputDir, videoId, "480p",  854,  480) &&
                            convertVideo(inputPath, outputDir, videoId, "720p", 1280,  720);

            if (success) {
                videoRepository.updateStatus(videoId, "COMPLETED");
                System.out.println(" Video #" + videoId + " COMPLETED");
            } else {
                videoRepository.updateStatus(videoId, "FAILED");
                System.out.println(" Video #" + videoId + " FAILED");
            }

        } catch (Exception e) {
            e.printStackTrace();
            videoRepository.updateStatus(videoId, "FAILED");
        }
    }

    // ─── GENERATE THUMBNAIL ───────────────────────────────────────
    private boolean generateThumbnail(String inputPath, Integer videoId) {
        try {
            String outputFileName = videoId + "_thumbnail.jpg";
            Path   outputPath     = Paths.get(thumbnailPath, outputFileName);

            System.out.println("Generating thumbnail for video #" + videoId);

            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "run", "--rm",
                    "-v", getVideosRootPath() + ":/videos",
                    "linuxserver/ffmpeg",
                    "-ss", "00:00:01",           // grab frame at 1 second
                    "-i", toDockerPath(inputPath),
                    "-vframes", "1",             // only 1 frame
                    "-f", "image2",
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
                // Save thumbnail path to DB
                String relativePath = outputFileName; // just the filename
                videoRepository.updateThumbnail(videoId, relativePath);
                System.out.println("Thumbnail saved: " + relativePath);
                return true;
            } else {
                System.out.println("Thumbnail generation failed — exit code: " + exitCode);
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ─── CONVERT VIDEO ────────────────────────────────────────────
    private boolean convertVideo(String inputPath, Path outputDir,
                                 Integer videoId, String quality,
                                 int width, int height) {
        try {
            String outputFileName = videoId + "_" + quality + ".mp4";
            Path   outputPath     = outputDir.resolve(outputFileName);

            System.out.println("Converting to " + quality + " → " + outputPath);

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
                System.out.println(" " + quality + " done");
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

    // ─── HELPERS ──────────────────────────────────────────────────
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