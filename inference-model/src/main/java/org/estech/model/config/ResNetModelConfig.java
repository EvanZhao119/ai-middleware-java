package org.estech.model.config;

import ai.djl.Device;
import ai.djl.engine.Engine;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.*;
import lombok.extern.slf4j.Slf4j;
import org.estech.common.constants.ModelConstants;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ResNetModelConfig {

    /**
     * 从 classpath 加载 ResNet18 模型与 synset 标签
     */
    public static ZooModel<Image, Classifications> loadModel() throws Exception {

        log.info("Loaded DJL Engine: " + Engine.getInstance().getEngineName());
        // === 从 classpath 加载 synset.txt ===
        List<String> synset;
        try (InputStream is = ResNetModelConfig.class
                .getClassLoader()
                .getResourceAsStream("models/resnet18/synset.txt")) {

            if (is == null) {
                throw new FileNotFoundException("Resource not found: models/resnet18/synset.txt");
            }

            synset = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.toList());
        }

        // === 从 classpath 加载 traced_resnet18.pt 模型文件 ===
        URL modelUrl = ResNetModelConfig.class
                .getClassLoader()
                .getResource("models/resnet18/traced_resnet18.pt");

        if (modelUrl == null) {
            throw new FileNotFoundException("Resource not found: models/resnet18/traced_resnet18.pt");
        }

        Path modelPath = Path.of(modelUrl.toURI());

        // === 创建 Translator（输入预处理 + 输出分类）===
        Translator<Image, Classifications> translator = new Translator<>() {
            @Override
            public NDList processInput(TranslatorContext ctx, Image input) throws Exception {
                NDManager manager = ctx.getNDManager();

                int width = ModelConstants.IMAGE_SIZE;
                int height = ModelConstants.IMAGE_SIZE;

                float[] mean = {0.485f, 0.456f, 0.406f};
                float[] std = {0.229f, 0.224f, 0.225f};

                // Resize
                Image scaled = ImageFactory.getInstance()
                        .fromImage(input.getWrappedImage())
                        .resize(width, height, true);

                BufferedImage buffered = (BufferedImage) scaled.getWrappedImage();

                int[] pixels = buffered.getRGB(0, 0, width, height, null, 0, width);
                float[] data = new float[3 * width * height];

                for (int i = 0; i < pixels.length; i++) {
                    int rgb = pixels[i];
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    int h = i / width;
                    int w = i % width;
                    int idx = h * width + w;

                    data[idx] = (r / 255f - mean[0]) / std[0];
                    data[width * height + idx] = (g / 255f - mean[1]) / std[1];
                    data[2 * width * height + idx] = (b / 255f - mean[2]) / std[2];
                }

                NDArray array = manager.create(data, new Shape(3, height, width)); // NCHW
                return new NDList(array);
            }

            @Override
            public Classifications processOutput(TranslatorContext ctx, NDList list) {
                return new Classifications(synset, list.singletonOrThrow());
            }

            @Override
            public Batchifier getBatchifier() {
                return Batchifier.STACK;
            }
        };

        // === 构建模型加载 Criteria ===
        Criteria<Image, Classifications> criteria = Criteria.builder()
                .setTypes(Image.class, Classifications.class)
                .optModelPath(modelPath)
                .optTranslator(translator)
                .optEngine("PyTorch") // macOS 版已验证可用；如切 ONNX 可改成 "OnnxRuntime"
                .optDevice(Device.cpu())
                .build();

        return ModelZoo.loadModel(criteria);
    }

    /**
     * 新增：用于 JNI/NDList 输入（不走 Translator）
     */
    public static ZooModel<NDList, Classifications> loadModelForNDList() throws Exception {
        log.info("Loaded DJL Engine (NDList mode): {}", Engine.getInstance().getEngineName());

        List<String> synset = loadSynset();
        URL modelUrl = getModelUrl();
        Path modelPath = Path.of(modelUrl.toURI());

        Translator<NDList, Classifications> translator = new NoOpTranslator(synset);

        Criteria<NDList, Classifications> criteria = Criteria.builder()
                .setTypes(NDList.class, Classifications.class)
                .optModelPath(modelPath)
                .optTranslator(translator)
                .optEngine("PyTorch")
                .optDevice(Device.cpu())
                .build();

        return ModelZoo.loadModel(criteria);
    }

    private static List<String> loadSynset() throws IOException {
        try (InputStream is = ResNetModelConfig.class
                .getClassLoader()
                .getResourceAsStream("models/resnet18/synset.txt")) {

            if (is == null) {
                throw new FileNotFoundException("Resource not found: models/resnet18/synset.txt");
            }

            return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.toList());
        }
    }

    private static URL getModelUrl() throws FileNotFoundException {
        URL modelUrl = ResNetModelConfig.class
                .getClassLoader()
                .getResource("models/resnet18/traced_resnet18.pt");
        if (modelUrl == null) {
            throw new FileNotFoundException("Resource not found: models/resnet18/traced_resnet18.pt");
        }
        return modelUrl;
    }

    /**
     * 内部类：NDList 模式用的空 Translator，只负责返回输出
     */
    private static class NoOpTranslator implements Translator<NDList, Classifications> {
        private final List<String> synset;

        public NoOpTranslator(List<String> synset) {
            this.synset = synset;
        }

        @Override
        public NDList processInput(TranslatorContext ctx, NDList input) {
            return input;
        }

        @Override
        public Classifications processOutput(TranslatorContext ctx, NDList list) {
            return new Classifications(synset, list.singletonOrThrow());
        }

        @Override
        public Batchifier getBatchifier() {
            return Batchifier.STACK;
        }
    }

}
