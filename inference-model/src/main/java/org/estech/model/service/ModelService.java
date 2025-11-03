package org.estech.model.service;

import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import org.estech.common.dto.ClassificationResult;
import org.estech.model.core.ImagePredictor;

import java.io.InputStream;
import java.nio.file.Path;

public class ModelService {

    private static final int DEFAULT_TOP_K = 5;

    public ClassificationResult classify(Path imagePath) throws Exception {
        return classify(imagePath, DEFAULT_TOP_K);
    }

    public ClassificationResult classify(Path imagePath, int topK) throws Exception {
        Image image = ImageFactory.getInstance().fromFile(imagePath);
        return predictInternal(image, topK);
    }

    public ClassificationResult classify(InputStream stream) throws Exception {
        return classify(stream, DEFAULT_TOP_K);
    }

    public ClassificationResult classify(InputStream stream, int topK) throws Exception {
        Image image = ImageFactory.getInstance().fromInputStream(stream);
        return predictInternal(image, topK);
    }

    public ClassificationResult classifyNDArray(NDArray nd, int topK) throws Exception {
        try (ImagePredictor predictor = new ImagePredictor()) {
            // 直接调用模型预测，不走 Translator.processInput()
            Classifications result = predictor.predict(new NDList(nd));
            return ClassificationResultAssembler.from(result, topK);
        }
    }

    private ClassificationResult predictInternal(Image image, int topK) throws Exception {
        try (ImagePredictor predictor = new ImagePredictor()) {
            Classifications result = predictor.predict(image);
            return ClassificationResultAssembler.from(result, topK);
        }
    }
}
