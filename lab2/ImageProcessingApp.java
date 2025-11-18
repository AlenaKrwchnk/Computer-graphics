import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.imageio.*;
import javax.swing.*;

public class ImageProcessingApp extends JFrame {

    private BufferedImage originalImage;
    private BufferedImage processedImage;
    private JLabel imageLabel;

    private JComboBox<String> morphOperationBox;
    private JComboBox<String> morphShapeBox;
    private JSpinner morphSizeSpinner;

    public ImageProcessingApp() {
        setTitle("Обработка изображений (резкость + морфология)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());


        JPanel controlPanel = new JPanel();
        JButton loadButton = new JButton("Загрузить изображение");
        JButton sharpenButton = new JButton("Повысить резкость");
        JButton morphButton = new JButton("Применить морфологию");

        controlPanel.add(loadButton);
        controlPanel.add(sharpenButton);

        controlPanel.add(new JLabel("Морфологическая операция:"));
        morphOperationBox = new JComboBox<>(new String[]{"Эрозия", "Дилатация", "Открытие", "Закрытие"});
        controlPanel.add(morphOperationBox);

        controlPanel.add(new JLabel("Форма элемента:"));
        morphShapeBox = new JComboBox<>(new String[]{"Квадрат", "Эллипс", "Крест"});
        controlPanel.add(morphShapeBox);

        controlPanel.add(new JLabel("Размер элемента:"));
        morphSizeSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        controlPanel.add(morphSizeSpinner);

        controlPanel.add(morphButton);

        add(controlPanel, BorderLayout.NORTH);


        imageLabel = new JLabel();
        JScrollPane scrollPane = new JScrollPane(imageLabel);
        add(scrollPane, BorderLayout.CENTER);

        loadButton.addActionListener(e -> loadImage());
        sharpenButton.addActionListener(e -> sharpenImage());
        morphButton.addActionListener(e -> applyMorphology());

        setSize(1000, 700);
        setLocationRelativeTo(null);
        setVisible(true);
    }


    private void loadImage() {
        JFileChooser chooser = new JFileChooser("img");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                originalImage = ImageIO.read(chooser.getSelectedFile());
                processedImage = deepCopy(originalImage);
                showImage(processedImage);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }


    private void showImage(BufferedImage img) {
        imageLabel.setIcon(new ImageIcon(img));
        imageLabel.repaint();
    }


    private void sharpenImage() {
        if (processedImage == null) return;

        float amount = 1.5f;  // сила резкости
        float[] blurKernel = {
                1/16f, 2/16f, 1/16f,
                2/16f, 4/16f, 2/16f,
                1/16f, 2/16f, 1/16f
        };

        ConvolveOp blurOp = new ConvolveOp(new Kernel(3, 3, blurKernel));
        BufferedImage blurred = blurOp.filter(processedImage, null);

        BufferedImage sharp = new BufferedImage(processedImage.getWidth(),
                processedImage.getHeight(),
                processedImage.getType());

        for (int y = 0; y < processedImage.getHeight(); y++) {
            for (int x = 0; x < processedImage.getWidth(); x++) {

                Color orig = new Color(processedImage.getRGB(x, y));
                Color blur = new Color(blurred.getRGB(x, y));

                int r = clamp(orig.getRed() + (int)((orig.getRed() - blur.getRed()) * amount));
                int g = clamp(orig.getGreen() + (int)((orig.getGreen() - blur.getGreen()) * amount));
                int b = clamp(orig.getBlue() + (int)((orig.getBlue() - blur.getBlue()) * amount));

                sharp.setRGB(x, y, new Color(r, g, b).getRGB());
            }
        }

        processedImage = sharp;
        showImage(processedImage);
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }


    private void applyMorphology() {
        if (processedImage == null) return;

        String operation = (String) morphOperationBox.getSelectedItem();
        String shape = (String) morphShapeBox.getSelectedItem();
        int size = (Integer) morphSizeSpinner.getValue();

        boolean[][] binary = binarize(processedImage);
        boolean[][] kernel = makeKernel(shape, size);

        switch (operation) {
            case "Эрозия":
                binary = erode(binary, kernel);
                break;
            case "Дилатация":
                binary = dilate(binary, kernel);
                break;
            case "Открытие":
                binary = erode(binary, kernel);
                binary = dilate(binary, kernel);
                break;
            case "Закрытие":
                binary = dilate(binary, kernel);
                binary = erode(binary, kernel);
                break;
        }

        processedImage = fromBinary(binary);
        showImage(processedImage);
    }

    private boolean[][] makeKernel(String shape, int size) {
        boolean[][] kernel = new boolean[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                switch (shape) {
                    case "Квадрат":
                        kernel[y][x] = true;
                        break;
                    case "Эллипс":
                        double dx = x - size / 2.0;
                        double dy = y - size / 2.0;
                        kernel[y][x] = dx*dx + dy*dy <= (size/2.0)*(size/2.0);
                        break;
                    case "Крест":
                        kernel[y][x] = (x == size/2 || y == size/2);
                        break;
                }
            }
        }
        return kernel;
    }

    private boolean[][] binarize(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        boolean[][] bin = new boolean[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color c = new Color(img.getRGB(x, y));
                int gray = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
                bin[y][x] = gray < 128;
            }
        }
        return bin;
    }

    private BufferedImage fromBinary(boolean[][] bin) {
        int h = bin.length;
        int w = bin[0].length;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = bin[y][x] ? 0 : 255;
                img.setRGB(x, y, new Color(v, v, v).getRGB());
            }
        }

        return img;
    }

    private boolean[][] erode(boolean[][] img, boolean[][] kernel) {
        int h = img.length;
        int w = img[0].length;
        int k = kernel.length;
        int half = k / 2;

        boolean[][] out = new boolean[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {

                boolean ok = true;

                outer:
                for (int ky = 0; ky < k; ky++) {
                    for (int kx = 0; kx < k; kx++) {
                        if (!kernel[ky][kx]) continue;

                        int yy = y + ky - half;
                        int xx = x + kx - half;

                        if (yy < 0 || yy >= h || xx < 0 || xx >= w || !img[yy][xx]) {
                            ok = false;
                            break outer;
                        }
                    }
                }

                out[y][x] = ok;
            }
        }
        return out;
    }

    private boolean[][] dilate(boolean[][] img, boolean[][] kernel) {
        int h = img.length;
        int w = img[0].length;
        int k = kernel.length;
        int half = k / 2;

        boolean[][] out = new boolean[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {

                boolean ok = false;

                outer:
                for (int ky = 0; ky < k; ky++) {
                    for (int kx = 0; kx < k; kx++) {
                        if (!kernel[ky][kx]) continue;

                        int yy = y + ky - half;
                        int xx = x + kx - half;

                        if (yy >= 0 && yy < h && xx >= 0 && xx < w && img[yy][xx]) {
                            ok = true;
                            break outer;
                        }
                    }
                }

                out[y][x] = ok;
            }
        }
        return out;
    }


    public static BufferedImage deepCopy(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        WritableRaster raster = bi.copyData(null);
        return new BufferedImage(cm, raster, cm.isAlphaPremultiplied(), null);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ImageProcessingApp::new);
    }
}
