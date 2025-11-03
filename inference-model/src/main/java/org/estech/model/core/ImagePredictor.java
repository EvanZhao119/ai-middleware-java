package org.estech.model.core;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.ndarray.NDList;
import ai.djl.repository.zoo.ZooModel;

public class ImagePredictor implements AutoCloseable {

    private Predictor<Image, Classifications> imagePredictor;
    private Predictor<NDList, Classifications> ndListPredictor;

    public ImagePredictor() throws Exception {
        ZooModel<Image, Classifications> imageModel = ModelManager.getImageModel();
        this.imagePredictor = imageModel.newPredictor();

        ZooModel<NDList, Classifications> ndListPredictor = ModelManager.getNDListModel();
        this.ndListPredictor = ndListPredictor.newPredictor();
    }

    public Classifications predict(Image image) throws Exception {
        if (imagePredictor == null) {
            throw new IllegalStateException("Image predictor not initialized.");
        }
        return imagePredictor.predict(image);
    }

    public Classifications predict(NDList ndList) throws Exception {
        if (ndListPredictor == null) {
            throw new IllegalStateException("NDList predictor not initialized.");
        }
        return ndListPredictor.predict(ndList);
    }

    @Override
    public void close() {
        if (imagePredictor != null) {
            imagePredictor.close();
        }
        if (ndListPredictor != null) {
            ndListPredictor.close();
        }
    }
}
