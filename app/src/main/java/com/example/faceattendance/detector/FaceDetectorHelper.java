package com.example.faceattendance.detector;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.*;
import java.util.List;

public class FaceDetectorHelper implements ImageAnalysis.Analyzer {

    public interface FaceCallback {
        void onFacesDetected(List<Face> faces);
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
        @SuppressWarnings("UnsafeOptInUsageError")
        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );
        detector.process(image)
                .addOnSuccessListener(faces -> callback.onFacesDetected(faces))
                .addOnFailureListener(Throwable::printStackTrace)
                .addOnCompleteListener(t -> imageProxy.close());
    }
}