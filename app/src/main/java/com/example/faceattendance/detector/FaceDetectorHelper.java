package com.example.faceattendance.detector;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;
import java.util.List;

public class FaceDetectorHelper implements ImageAnalysis.Analyzer {

    public interface FaceCallback {
        void onFacesDetected(List<Face> faces, int w, int h);
    }

    private final FaceDetector detector;
    private final FaceCallback callback;

    public FaceDetectorHelper(FaceCallback callback) {
        this.callback = callback;
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build();
        detector = FaceDetection.getClient(options);
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        int rotation = imageProxy.getImageInfo().getRotationDegrees();

        @SuppressWarnings("UnsafeOptInUsageError")
        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                rotation
        );

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    // Khi xoay 90°/270°, cảm biến landscape nhưng ML Kit
                    // trả bounding box theo ảnh đã xoay (portrait)
                    // → hoán đổi w/h để scale overlay đúng
                    int w, h;
                    if (rotation == 90 || rotation == 270) {
                        w = imageProxy.getHeight();
                        h = imageProxy.getWidth();
                    } else {
                        w = imageProxy.getWidth();
                        h = imageProxy.getHeight();
                    }
                    callback.onFacesDetected(faces, w, h);
                })
                .addOnFailureListener(Throwable::printStackTrace)
                .addOnCompleteListener(t -> imageProxy.close());
    }
}