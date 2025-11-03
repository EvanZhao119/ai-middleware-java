package org.estech.model.core;

import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.ZooModel;
import org.estech.model.config.ResNetModelConfig;

public class ModelManager {
    private static volatile ZooModel<Image, Classifications> model;

    private ModelManager() {}

    public static ZooModel<Image, Classifications> getModel() throws Exception {
        if (model == null) {
            synchronized (ModelManager.class) {
                if (model == null) {
                    model = ResNetModelConfig.loadModel();
                }
            }
        }
        return model;
    }

    public static void close() {
        if (model != null) {
            model.close();
            model = null;
        }
    }
}
