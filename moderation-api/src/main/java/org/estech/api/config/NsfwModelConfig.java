package org.estech.api.config;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import ai.djl.modality.cv.ImageFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Configuration
public class NsfwModelConfig {

    private ZooModel<NDList, Classifications> model;
    private List<String> synset;

    @PostConstruct
    public void init() throws Exception {
        Path modelPath = extractToTemp("models/nsfw.onnx", "nsfw", ".onnx");
        Path labelsPath = extractToTemp("models/labels.txt", "labels", ".txt");

        // Parse labels
        synset = Files.readAllLines(labelsPath, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                .map(line -> {
                    String[] parts = line.split("\\s+", 2);
                    return parts.length == 2 ? parts[1] : parts[0];
                })
                .toList();

        Translator<NDList, Classifications> translator = new Translator<>() {
            @Override
            public NDList processInput(TranslatorContext ctx, NDList input) {
                return input;
            }

            @Override
            public Classifications processOutput(TranslatorContext ctx, NDList list) {
                NDArray output = list.singletonOrThrow();
                return new Classifications(synset, output);
            }

            @Override
            public Batchifier getBatchifier() {
                return null;
            }
        };

        Criteria<NDList, Classifications> criteria = Criteria.builder()
                .setTypes(NDList.class, Classifications.class)
                .optModelPath(modelPath)
                .optTranslator(translator)
                .optEngine("OnnxRuntime")
                .build();

        model = ModelZoo.loadModel(criteria);

        // Warmup
        try (NDManager manager = NDManager.newBaseManager();
             Predictor<NDList, Classifications> predictor = model.newPredictor()) {

            BufferedImage dummyImage = new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB);
            NDArray preprocessed = preprocess(dummyImage, manager);
            NDList input = new NDList(preprocessed);
            predictor.predict(input);
        }
    }

    @Bean
    public ZooModel<NDList, Classifications> nsfwModel() {

        return model;
    }

    @PreDestroy
    public void close() {
        if (model != null) {
            model.close();
        }
    }

    private Path extractToTemp(String classpath, String prefix, String suffix) throws Exception {
        ClassPathResource cpr = new ClassPathResource(classpath);
        Path tmp = Files.createTempFile(prefix, suffix);
        try (InputStream in = cpr.getInputStream()) {
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        tmp.toFile().deleteOnExit();
        return tmp;
    }

    public static NDArray preprocess(BufferedImage img, NDManager manager) {
        int width = 224;
        int height = 224;
        float[] mean = {0.485f, 0.456f, 0.406f};
        float[] std = {0.229f, 0.224f, 0.225f};

        ai.djl.modality.cv.Image djlImage = ImageFactory.getInstance().fromImage(img);
        ai.djl.modality.cv.Image scaled = djlImage.resize(width, height, true);
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

            data[idx] = ((r / 255f) - mean[0]) / std[0];
            data[width * height + idx] = ((g / 255f) - mean[1]) / std[1];
            data[2 * width * height + idx] = ((b / 255f) - mean[2]) / std[2];
        }

        return manager.create(data, new Shape(1, 3, height, width)); // NCHW
    }

}
