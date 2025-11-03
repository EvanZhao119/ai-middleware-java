package org.estech.model.core;

import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.ZooModel;
import org.estech.model.config.ResNetModelConfig;
import ai.djl.ndarray.NDList;

public class ModelManager {
    private static volatile ZooModel<Image, Classifications> imageModel;
    private static volatile ZooModel<NDList, Classifications> ndListModel;

    private ModelManager() {}

    public static ZooModel<Image, Classifications> getImageModel() throws Exception {
        if (imageModel == null) {
            synchronized (ModelManager.class) {
                if (imageModel == null) {
                    imageModel = ResNetModelConfig.loadModel();
                }
            }
        }
        return imageModel;
    }

    public static ZooModel<NDList, Classifications> getNDListModel() throws Exception {
        if (ndListModel == null) {
            synchronized (ModelManager.class) {
                if (ndListModel == null) {
                    ndListModel = ResNetModelConfig.loadModelForNDList();
                }
            }
        }
        return ndListModel;
    }

    public static void close() {
        if (imageModel != null) {
            imageModel.close();
            imageModel = null;
        }
        if (ndListModel != null) {
            ndListModel.close();
            ndListModel = null;
        }
    }
}
