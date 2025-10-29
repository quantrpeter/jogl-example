package org.hkprog.jogl.example;

import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.GLUquadric;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;

import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class JoglExample implements GLEventListener {

    private final GLU glu = new GLU();
    private GLUquadric sphereQuadric;

    private float rotationXDeg = 20.0f;
    private float rotationYDeg = -30.0f;
    private float cameraZoom = -20.0f; // move camera back along -Z

    private int lastMouseX;
    private int lastMouseY;
    private boolean isDragging = false;

    private double orbitAngleRad = 0.0; // moon orbital angle
    private long lastNanos = System.nanoTime();

    // Scene parameters
    private static final float EARTH_RADIUS = 2.0f;
    private static final float MOON_RADIUS = 0.6f;
    private static final float MOON_ORBIT_RADIUS = 6.0f;
    private static final double MOON_ORBIT_PERIOD_SEC = 8.0; // seconds per orbit

    // Axial rotation
    private float earthSpinDeg = 0.0f;
    private float moonSpinDeg = 0.0f;
    private static final double EARTH_ROTATION_PERIOD_SEC = 6.0; // seconds per full spin (demo)
    private static final double MOON_ROTATION_PERIOD_SEC = 10.0; // seconds per full spin (demo)

    // Textures
    private Texture earthTexture;
    private Texture moonTexture;

    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        gl.glClearColor(0.02f, 0.02f, 0.06f, 1.0f);
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glDepthFunc(GL2.GL_LEQUAL);
        gl.glEnable(GL2.GL_CULL_FACE);
        gl.glCullFace(GL2.GL_BACK);

        // Lighting
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT0);
        float[] lightAmbient = {0.05f, 0.05f, 0.05f, 1.0f};
        float[] lightDiffuse = {0.9f, 0.9f, 0.9f, 1.0f};
        float[] lightPosition = {10.0f, 8.0f, 12.0f, 1.0f};
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_AMBIENT, lightAmbient, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, lightDiffuse, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, lightPosition, 0);

        // Quadric for spheres
        sphereQuadric = glu.gluNewQuadric();
        glu.gluQuadricNormals(sphereQuadric, GLU.GLU_SMOOTH);
        glu.gluQuadricTexture(sphereQuadric, true);

        // Normalize normals for scaled objects
        gl.glEnable(GL2.GL_NORMALIZE);

        // Texturing
        gl.glEnable(GL2.GL_TEXTURE_2D);
        loadTextures(gl);
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        if (sphereQuadric != null) {
            sphereQuadric = null; // let GC clean; JOGL handles native cleanup
        }
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        updateAnimation();

        GL2 gl = drawable.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();

        // Camera transform
        gl.glTranslatef(0.0f, 0.0f, cameraZoom);
        gl.glRotatef(rotationXDeg, 1.0f, 0.0f, 0.0f);
        gl.glRotatef(rotationYDeg, 0.0f, 1.0f, 0.0f);

        // Draw Earth at origin
        gl.glPushMatrix();
        gl.glRotatef(23.5f, 0.0f, 0.0f, 1.0f); // axial tilt
        gl.glRotatef(earthSpinDeg, 0.0f, 1.0f, 0.0f);
        if (earthTexture != null) {
            setMaterial(gl, new float[]{1.0f, 1.0f, 1.0f, 1.0f});
            earthTexture.enable(gl);
            earthTexture.bind(gl);
        } else {
            setMaterial(gl, new float[]{0.1f, 0.2f, 0.8f, 1.0f});
        }
        glu.gluSphere(sphereQuadric, EARTH_RADIUS, 48, 32);
        if (earthTexture != null) {
            earthTexture.disable(gl);
        }
        gl.glPopMatrix();

        // Draw Moon orbiting Earth
        gl.glPushMatrix();
        float moonX = (float) (MOON_ORBIT_RADIUS * Math.cos(orbitAngleRad));
        float moonZ = (float) (MOON_ORBIT_RADIUS * Math.sin(orbitAngleRad));
        gl.glTranslatef(moonX, 0.0f, moonZ);
        gl.glRotatef(moonSpinDeg, 0.0f, 1.0f, 0.0f);
        if (moonTexture != null) {
            setMaterial(gl, new float[]{1.0f, 1.0f, 1.0f, 1.0f});
            moonTexture.enable(gl);
            moonTexture.bind(gl);
        } else {
            setMaterial(gl, new float[]{0.7f, 0.7f, 0.7f, 1.0f});
        }
        glu.gluSphere(sphereQuadric, MOON_RADIUS, 32, 24);
        if (moonTexture != null) {
            moonTexture.disable(gl);
        }
        gl.glPopMatrix();

        // Optional: simple orbit ring to visualize path
        drawOrbitRing(gl, MOON_ORBIT_RADIUS);
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glViewport(0, 0, width, Math.max(height, 1));

        float aspect = (height == 0) ? 1.0f : (float) width / (float) height;
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        glu.gluPerspective(60.0f, aspect, 0.1f, 1000.0f);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    private void setMaterial(GL2 gl, float[] rgba) {
        float[] ambient = {rgba[0] * 0.2f, rgba[1] * 0.2f, rgba[2] * 0.2f, 1.0f};
        float[] diffuse = {rgba[0], rgba[1], rgba[2], 1.0f};
        float[] specular = {0.9f, 0.9f, 0.9f, 1.0f};
        float shininess = 32.0f;
        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT, ambient, 0);
        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, diffuse, 0);
        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, specular, 0);
        gl.glMaterialf(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, shininess);
    }

    private void drawOrbitRing(GL2 gl, float radius) {
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glColor3f(0.4f, 0.4f, 0.5f);
        gl.glBegin(GL2.GL_LINE_LOOP);
        int segments = 128;
        for (int i = 0; i < segments; i++) {
            double ang = (2.0 * Math.PI * i) / segments;
            float x = (float) (radius * Math.cos(ang));
            float z = (float) (radius * Math.sin(ang));
            gl.glVertex3f(x, 0.0f, z);
        }
        gl.glEnd();
        gl.glEnable(GL2.GL_LIGHTING);
    }

    private void updateAnimation() {
        long now = System.nanoTime();
        long delta = now - lastNanos;
        lastNanos = now;
        double dt = delta / 1_000_000_000.0; // seconds
        double angularSpeed = (2.0 * Math.PI) / MOON_ORBIT_PERIOD_SEC; // rad/sec
        orbitAngleRad = (orbitAngleRad + angularSpeed * dt) % (2.0 * Math.PI);
        earthSpinDeg = (float) ((earthSpinDeg + (360.0 * dt / EARTH_ROTATION_PERIOD_SEC)) % 360.0);
        moonSpinDeg = (float) ((moonSpinDeg + (360.0 * dt / MOON_ROTATION_PERIOD_SEC)) % 360.0);
    }

    private void loadTextures(GL2 gl) {
        earthTexture = tryLoadTextureFromResources(gl, "/textures/earth.jpg");
        if (earthTexture == null) {
            earthTexture = tryLoadTextureFromResources(gl, "/textures/earth.png");
        }
        moonTexture = tryLoadTextureFromResources(gl, "/textures/moon.jpg");
        if (moonTexture == null) {
            moonTexture = tryLoadTextureFromResources(gl, "/textures/moon.png");
        }

        if (earthTexture == null) {
            earthTexture = createProceduralEarthTexture(gl);
        }
        if (moonTexture == null) {
            moonTexture = createProceduralMoonTexture(gl);
        }
    }

    private Texture tryLoadTextureFromResources(GL2 gl, String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) return null;
            String lower = resourcePath.toLowerCase();
            String ext = lower.endsWith(".png") ? "png" : lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "jpg" : null;
            if (ext == null) return null;
            return TextureIO.newTexture(in, true, ext);
        } catch (IOException e) {
            return null;
        }
    }

    private Texture createProceduralEarthTexture(GL2 gl) {
        int width = 512;
        int height = 256;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(15, 40, 120));
        g.fillRect(0, 0, width, height);
        g.setColor(new java.awt.Color(20, 140, 60));
        for (int i = 0; i < 12; i++) {
            int w = 30 + (i * 7) % 90;
            int h = 15 + (i * 5) % 60;
            int x = (int) ((i * 41.3) % (width - w));
            int y = (int) ((i * 23.7) % (height - h));
            g.fillOval(x, y, w, h);
        }
        g.dispose();
        return AWTTextureIO.newTexture(gl.getGLProfile(), img, true);
    }

    private Texture createProceduralMoonTexture(GL2 gl) {
        int width = 256;
        int height = 128;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(170, 170, 170));
        g.fillRect(0, 0, width, height);
        g.setColor(new java.awt.Color(140, 140, 140));
        for (int i = 0; i < 40; i++) {
            int r = 3 + (i * 3) % 14;
            int x = (int) ((i * 19.1) % (width - r));
            int y = (int) ((i * 29.7) % (height - r));
            g.fillOval(x, y, r, r);
        }
        g.dispose();
        return AWTTextureIO.newTexture(gl.getGLProfile(), img, true);
    }

    private void attachInputHandlers(GLCanvas canvas) {
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT -> rotationYDeg -= 3.0f;
                    case KeyEvent.VK_RIGHT -> rotationYDeg += 3.0f;
                    case KeyEvent.VK_UP -> rotationXDeg -= 3.0f;
                    case KeyEvent.VK_DOWN -> rotationXDeg += 3.0f;
                    case KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS -> cameraZoom += 1.0f; // zoom in
                    case KeyEvent.VK_MINUS -> cameraZoom -= 1.0f; // zoom out
                    case KeyEvent.VK_R -> {
                        rotationXDeg = 20.0f;
                        rotationYDeg = -30.0f;
                        cameraZoom = -20.0f;
                    }
                    default -> { }
                }
                canvas.display();
            }
        });

        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                isDragging = true;
                lastMouseX = e.getX();
                lastMouseY = e.getY();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isDragging = false;
            }
        });

        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (!isDragging) return;
                int dx = e.getX() - lastMouseX;
                int dy = e.getY() - lastMouseY;
                rotationYDeg += dx * 0.4f;
                rotationXDeg += dy * 0.4f;
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                canvas.display();
            }
        });

        canvas.addMouseWheelListener((MouseWheelEvent e) -> {
            cameraZoom += e.getWheelRotation() * 1.0f;
            canvas.display();
        });
    }

    public static void main(String[] args) {
        GLProfile profile = GLProfile.get(GLProfile.GL2);
        GLCapabilities capabilities = new GLCapabilities(profile);
        capabilities.setDepthBits(24);

        GLCanvas canvas = new GLCanvas(capabilities);
        JoglExample renderer = new JoglExample();
        canvas.addGLEventListener(renderer);
        renderer.attachInputHandlers(canvas);

        Frame frame = new Frame("JOGL Earth–Moon Orbit");
        frame.add(canvas);
        frame.setSize(1000, 700);
        frame.setLocationRelativeTo(null);

        final FPSAnimator animator = new FPSAnimator(canvas, 60, true);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                new Thread(() -> {
                    if (animator.isStarted()) {
                        animator.stop();
                    }
                    System.exit(0);
                }).start();
            }
        });

        frame.setVisible(true);
        animator.start();
        canvas.requestFocusInWindow();
    }
}
