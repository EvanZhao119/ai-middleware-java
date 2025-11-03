package org.estech.model.core;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.ZooModel;

public class ImagePredictor implements AutoCloseable {
    private final Predictor<Image, Classifications> predictor;

    public ImagePredictor() throws Exception {
        ZooModel<Image, Classifications> model = ModelManager.getModel();
        this.predictor = model.newPredictor();
    }

    public Classifications predict(Image image) throws Exception {
        return predictor.predict(image);
    }

    @Override
    public void close() {
        predictor.close();
    }
}
