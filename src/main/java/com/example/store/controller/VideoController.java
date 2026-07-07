package com.example.store.controller;

import com.example.store.config.RabbitMQConfig;
import com.example.store.model.VideoProcessed;
import com.example.store.model.VideoUpload;
import com.example.store.repository.VideoRepository;
import com.example.store.service.VideoProcessingService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/videos")
public class VideoController {

    private final VideoRepository videoRepository;
    private final VideoProcessingService videoProcessingService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${video.upload.path}")
    private String uploadPath;

    @Value("${video.processed.path}")
    private String processedPath;

    public VideoController(VideoRepository videoRepository,
                           VideoProcessingService videoProcessingService,
                           RabbitTemplate rabbitTemplate) {
        this.videoRepository        = videoRepository;
        this.videoProcessingService = videoProcessingService;
        this.rabbitTemplate         = rabbitTemplate;
    }

    // ─── UPLOAD VIDEO ─────────────────────────────────────────────
    @PostMapping("/upload")
    public ResponseEntity<?> uploadVideo(@RequestParam("file") MultipartFile file) {

        // Validate file type
        String originalName = file.getOriginalFilename();
        if (originalName == null ||
                (!originalName.toLowerCase().endsWith(".mp4") &&
                        !originalName.toLowerCase().endsWith(".mov"))) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Only .mp4 and .mov files are allowed")
            );
        }

        // Get logged-in username
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String uploadedBy = auth.getName();

        try {
            // Generate unique filename to avoid conflicts
            String ext      = originalName.substring(originalName.lastIndexOf('.'));
            String fileName = UUID.randomUUID().toString() + ext;
            Path   filePath = Paths.get(uploadPath, fileName);

            // Save file to uploads folder
            Files.createDirectories(filePath.getParent());
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Save record to DB with PENDING status
            Integer videoId = videoRepository.save(originalName, filePath.toString(), uploadedBy);

            System.out.println("Video uploaded: " + originalName + " → " + filePath);

            return ResponseEntity.ok(Map.of(
                    "message",  "Video uploaded successfully",
                    "video_id", videoId,
                    "status",   "PENDING"
            ));

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Failed to save file: " + e.getMessage())
            );
        }
    }

    // ─── GET ALL VIDEOS ───────────────────────────────────────────
    @GetMapping
    public ResponseEntity<List<VideoUpload>> getAllVideos() {
        return ResponseEntity.ok(videoRepository.findAll());
    }

    // ─── GET PENDING VIDEOS ───────────────────────────────────────
    @GetMapping("/pending")
    public ResponseEntity<List<VideoUpload>> getPendingVideos() {
        return ResponseEntity.ok(videoRepository.findPending());
    }

    // ─── GET COMPLETED VIDEOS ─────────────────────────────────────
    @GetMapping("/completed")
    public ResponseEntity<List<VideoUpload>> getCompletedVideos() {
        return ResponseEntity.ok(videoRepository.findCompleted());
    }

    // ─── GET PROCESSED FILES FOR A VIDEO ──────────────────────────
    @GetMapping("/{id}/processed")
    public ResponseEntity<List<VideoProcessed>> getProcessedFiles(@PathVariable Integer id) {
        return ResponseEntity.ok(videoRepository.findProcessedByVideoId(id));
    }

    // ─── PROCESS VIDEO ────────────────────────────────────────────
    @PostMapping("/{id}/process")
    public ResponseEntity<?> processVideo(@PathVariable Integer id) {
        VideoUpload video = videoRepository.findById(id);

        if (video == null) {
            return ResponseEntity.notFound().build();
        }

        // Atomically claim the video (PENDING -> PROCESSING), guards against double-clicks
        boolean claimed = videoRepository.markProcessing(id);
        if (!claimed) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Video is already " + video.getStatus())
            );
        }

        // Publish job to RabbitMQ — VideoProcessingService's @RabbitListener picks it up
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.VIDEO_EXCHANGE,
                RabbitMQConfig.VIDEO_KEY,
                id
        );

        return ResponseEntity.accepted().body(Map.of(
                "message",  "Video processing queued",
                "video_id", id,
                "status",   "PROCESSING"
        ));
    }

    // ─── STREAM/PLAY VIDEO ────────────────────────────────────────
    @GetMapping("/stream/{filename:.+}")
    public ResponseEntity<Resource> streamVideo(@PathVariable String filename) {
        try {
            Path videoPath = Paths.get(processedPath, filename);
            Resource resource = new UrlResource(videoPath.toUri());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}