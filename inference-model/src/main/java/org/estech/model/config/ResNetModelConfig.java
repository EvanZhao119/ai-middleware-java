package org.estech.model.config;

import ai.djl.Device;
import ai.djl.engine.Engine;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ResNetModelConfig {

    private static final String MODEL_PATH = "models/resnet18/traced_resnet18.pt";
    private static final String SYNSET_PATH = "models/resnet18/synset.txt";

    /**
     * 从 classpath 加载模型（Image 模式）
     */
    public static ZooModel<Image, Classifications> loadModel() throws Exception {
        log.info("Loaded DJL Engine: {}", Engine.getInstance().getEngineName());

        List<String> synset = loadSynset();
        Path modelPath = getModelPath();

        Translator<Image, Classifications> translator = buildImageTranslator(synset);

        Criteria<Image, Classifications> criteria = Criteria.builder()
                .setTypes(Image.class, Classifications.class)
                .optModelPath(modelPath)
                .optTranslator(translator)
                .optEngine("PyTorch")
                .optDevice(Device.cpu())
                .build();

        return ModelZoo.loadModel(criteria);
    }

    /**
     * 从 classpath 加载模型（NDList 模式）
     */
    public static ZooModel<NDList, Classifications> loadModelForNDList() throws Exception {
        log.info("Loaded DJL Engine (NDList mode): {}", Engine.getInstance().getEngineName());

        List<String> synset = loadSynset();
        Path modelPath = getModelPath();

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

    /**
     * 读取 synset.txt，无论 jar 内外
     */
    private static List<String> loadSynset() throws IOException {
        try (InputStream is = ResNetModelConfig.class.getClassLoader().getResourceAsStream(SYNSET_PATH)) {
            if (is == null) {
                throw new FileNotFoundException("Resource not found: " + SYNSET_PATH);
            }
            return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.toList());
        }
    }

    /**
     * 获取模型路径：如果在 jar 内，则解压到临时文件再返回 Path
     */
    private static Path getModelPath() throws IOException {
        URL modelUrl = ResNetModelConfig.class.getClassLoader().getResource(MODEL_PATH);
        if (modelUrl == null) {
            throw new FileNotFoundException("Resource not found: " + MODEL_PATH);
        }

        String protocol = modelUrl.getProtocol();
        if ("jar".equals(protocol)) {
            log.info("Detected jar resource, extracting model to temp file...");
            try (InputStream is = ResNetModelConfig.class.getClassLoader().getResourceAsStream(MODEL_PATH)) {
                File tempFile = Files.createTempFile("resnet18-", ".pt").toFile();
                tempFile.deleteOnExit();
                try (OutputStream os = new FileOutputStream(tempFile)) {
                    is.transferTo(os);
                }
                log.info("Model extracted to temporary file: {}", tempFile.getAbsolutePath());
                return tempFile.toPath();
            }
        } else {
            // IDE or exploded-jar 模式
            return Path.of(modelUrl.getPath());
        }
    }

    /**
     * 图像输入的 Translator
     */
    private static Translator<Image, Classifications> buildImageTranslator(List<String> synset) {
        return new Translator<>() {
            @Override
            public NDList processInput(TranslatorContext ctx, Image input) throws Exception {
                int width = ModelConstants.IMAGE_SIZE;
                int height = ModelConstants.IMAGE_SIZE;

                float[] mean = {0.485f, 0.456f, 0.406f};
                float[] std = {0.229f, 0.224f, 0.225f};

                Image scaled = ImageFactory.getInstance().fromImage(input.getWrappedImage()).resize(width, height, true);
                BufferedImage buffered = (BufferedImage) scaled.getWrappedImage();

                int[] pixels = buffered.getRGB(0, 0, width, height, null, 0, width);
                float[] data = new float[3 * width * height];

                for (int i = 0; i < pixels.length; i++) {
                    int rgb = pixels[i];
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    int h = i / width, w = i % width, idx = h * width + w;
                    data[idx] = (r / 255f - mean[0]) / std[0];
                    data[width * height + idx] = (g / 255f - mean[1]) / std[1];
                    data[2 * width * height + idx] = (b / 255f - mean[2]) / std[2];
                }

                NDArray array = ctx.getNDManager().create(data, new Shape(3, height, width));
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
    }

    /**
     * NDList 模式下的空 Translator
     */
    private static class NoOpTranslator implements Translator<NDList, Classifications> {
        private final List<String> synset;

        public NoOpTranslator(List<String> synset) {
            this.synset = synset;
        }

        @Override
        public NDList processInput(TranslatorContext ctx, NDList input) { return input; }

        @Override
        public Classifications processOutput(TranslatorContext ctx, NDList list) {
            return new Classifications(synset, list.singletonOrThrow());
        }

        @Override
        public Batchifier getBatchifier() { return Batchifier.STACK; }
    }
}
