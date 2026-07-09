package com.example.store.controller;

import com.example.store.model.VideoProcessed;
import com.example.store.model.VideoUpload;
import com.example.store.repository.VideoRepository;
import com.example.store.service.VideoProcessingService;
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

    private final VideoRepository        videoRepository;
    private final VideoProcessingService videoProcessingService;

    @Value("${video.upload.path}")
    private String uploadPath;

    @Value("${video.processed.path}")
    private String processedPath;

    @Value("${video.thumbnail.path}")
    private String thumbnailPath;

    public VideoController(VideoRepository videoRepository,
                           VideoProcessingService videoProcessingService) {
        this.videoRepository        = videoRepository;
        this.videoProcessingService = videoProcessingService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadVideo(@RequestParam("file") MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null ||
                (!originalName.toLowerCase().endsWith(".mp4") &&
                        !originalName.toLowerCase().endsWith(".mov"))) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Only .mp4 and .mov files are allowed")
            );
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String uploadedBy = auth.getName();

        try {
            String ext      = originalName.substring(originalName.lastIndexOf('.'));
            String fileName = UUID.randomUUID().toString() + ext;
            Path   filePath = Paths.get(uploadPath, fileName);

            Files.createDirectories(filePath.getParent());
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            Integer videoId = videoRepository.save(originalName, filePath.toString(), uploadedBy);
            System.out.println(" Video uploaded: " + originalName + " → " + filePath);

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

    @GetMapping
    public ResponseEntity<List<VideoUpload>> getAllVideos() {
        return ResponseEntity.ok(videoRepository.findAll());
    }

    @GetMapping("/pending")
    public ResponseEntity<List<VideoUpload>> getPendingVideos() {
        return ResponseEntity.ok(videoRepository.findPending());
    }

    @GetMapping("/completed")
    public ResponseEntity<List<VideoUpload>> getCompletedVideos() {
        return ResponseEntity.ok(videoRepository.findCompleted());
    }

    @GetMapping("/{id}/processed")
    public ResponseEntity<List<VideoProcessed>> getProcessedFiles(@PathVariable Integer id) {
        return ResponseEntity.ok(videoRepository.findProcessedByVideoId(id));
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<?> processVideo(@PathVariable Integer id) {
        VideoUpload video = videoRepository.findById(id);
        if (video == null) return ResponseEntity.notFound().build();

        if (!"PENDING".equals(video.getStatus())) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Video is already " + video.getStatus())
            );
        }

        videoProcessingService.processNow(video);

        return ResponseEntity.accepted().body(Map.of(
                "message",  "Video processing started",
                "video_id", id,
                "status",   "PROCESSING"
        ));
    }

    // ─── STREAM VIDEO ─────────────────────────────────────────────
    @GetMapping("/stream/{videoId}/{filename:.+}")
    public ResponseEntity<Resource> streamVideo(@PathVariable Integer videoId,
                                                @PathVariable String filename) {
        try {
            Path videoPath = Paths.get(processedPath, String.valueOf(videoId), filename);
            Resource resource = new UrlResource(videoPath.toUri());

            if (!resource.exists()) return ResponseEntity.notFound().build();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/thumbnail/{filename:.+}")
    public ResponseEntity<Resource> getThumbnail(@PathVariable String filename) {
        try {
            Path thumbPath = Paths.get(thumbnailPath, filename);
            Resource resource = new UrlResource(thumbPath.toUri());

            if (!resource.exists()) return ResponseEntity.notFound().build();

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}