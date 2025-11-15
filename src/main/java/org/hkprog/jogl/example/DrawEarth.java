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
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;

import java.awt.Frame;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;

public class DrawEarth implements GLEventListener {

    private final GLU glu = new GLU();
    private GLUquadric sphereQuadric;

    // Camera controls
    private float rotationXDeg = 20.0f;
    private float rotationYDeg = -30.0f;
    private float cameraZoom = -10.0f;

    // Mouse interaction
    private int lastMouseX;
    private int lastMouseY;
    private boolean isDragging = false;

    // Earth parameters
    private static final float EARTH_RADIUS = 3.0f;
    private float earthSpinDeg = 0.0f;
    private float textureOffsetDeg = 90.0f; // Adjust texture alignment
    private static final double EARTH_ROTATION_PERIOD_SEC = 60.0; // 60 seconds per full rotation

    // Animation
    private long lastNanos = System.nanoTime();

    // Texture
    private Texture earthTexture;

    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        // Background color (space black)
        gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        
        // Enable depth testing
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glDepthFunc(GL2.GL_LEQUAL);
        
        // Enable backface culling
        gl.glEnable(GL2.GL_CULL_FACE);
        gl.glCullFace(GL2.GL_BACK);

        // Setup lighting
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT0);
        
        float[] lightAmbient = {0.3f, 0.3f, 0.3f, 1.0f};
        float[] lightDiffuse = {1.0f, 1.0f, 1.0f, 1.0f};
        float[] lightSpecular = {1.0f, 1.0f, 1.0f, 1.0f};
        float[] lightPosition = {5.0f, 5.0f, 10.0f, 1.0f};
        
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_AMBIENT, lightAmbient, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, lightDiffuse, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_SPECULAR, lightSpecular, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, lightPosition, 0);

        // Setup sphere quadric
        sphereQuadric = glu.gluNewQuadric();
        glu.gluQuadricNormals(sphereQuadric, GLU.GLU_SMOOTH);
        glu.gluQuadricTexture(sphereQuadric, true);

        // Enable normal normalization
        gl.glEnable(GL2.GL_NORMALIZE);

        // Enable 2D texturing
        gl.glEnable(GL2.GL_TEXTURE_2D);
        
        // Load Earth texture
        loadTextures(gl);
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        if (sphereQuadric != null) {
            glu.gluDeleteQuadric(sphereQuadric);
            sphereQuadric = null;
        }
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        updateAnimation();

        GL2 gl = drawable.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();

        // Apply camera transformations
        gl.glTranslatef(0.0f, 0.0f, cameraZoom);
        gl.glRotatef(rotationXDeg, 1.0f, 0.0f, 0.0f);
        gl.glRotatef(rotationYDeg, 0.0f, 1.0f, 0.0f);

        // Draw Earth
        drawEarth(gl);
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glViewport(0, 0, width, Math.max(height, 1));

        float aspect = (height == 0) ? 1.0f : (float) width / (float) height;
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        glu.gluPerspective(45.0, aspect, 0.1, 100.0);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    private void drawEarth(GL2 gl) {
        gl.glPushMatrix();

        // Apply Earth's axial tilt (23.5 degrees) - tilt the entire coordinate system
        gl.glRotatef(23.5f, 0.0f, 0.0f, 1.0f);
        
        // Apply Earth's rotation with texture offset around the tilted Y-axis
        gl.glRotatef(earthSpinDeg + textureOffsetDeg, 0.0f, 1.0f, 0.0f);

        // Set material properties
        setMaterial(gl, new float[]{1.0f, 1.0f, 1.0f, 1.0f});

        if (earthTexture != null) {
            earthTexture.enable(gl);
            earthTexture.bind(gl);
        }

        // gluSphere generates a sphere with poles along Y-axis
        // Rotate 90 degrees around X to align gluSphere's poles with our Y-axis properly
        gl.glRotatef(-90.0f, 1.0f, 0.0f, 0.0f);
        
        // Draw the sphere
        glu.gluSphere(sphereQuadric, EARTH_RADIUS, 64, 64);

        if (earthTexture != null) {
            earthTexture.disable(gl);
        }

        // Draw rotation axis through the poles
        drawRotationAxis(gl);

        gl.glPopMatrix();
    }

    private void drawRotationAxis(GL2 gl) {
        // Disable lighting for the axis so it's always visible
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glDisable(GL2.GL_TEXTURE_2D);
        
        float axisLength = EARTH_RADIUS * 1.5f;
        
        // Draw the main axis line (yellow)
        gl.glColor3f(1.0f, 1.0f, 0.0f);
        gl.glLineWidth(3.0f);
        gl.glBegin(GL2.GL_LINES);
        gl.glVertex3f(0.0f, 0.0f, -axisLength);  // South pole
        gl.glVertex3f(0.0f, 0.0f, axisLength);   // North pole
        gl.glEnd();
        
        // Re-enable lighting and texture
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_TEXTURE_2D);
        gl.glLineWidth(1.0f);
    }

    private void setMaterial(GL2 gl, float[] rgba) {
        float[] ambient = {rgba[0] * 0.3f, rgba[1] * 0.3f, rgba[2] * 0.3f, rgba[3]};
        float[] diffuse = {rgba[0], rgba[1], rgba[2], rgba[3]};
        float[] specular = {0.5f, 0.5f, 0.5f, 1.0f};
        float shininess = 32.0f;
        
        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT, ambient, 0);
        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_DIFFUSE, diffuse, 0);
        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, specular, 0);
        gl.glMaterialf(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, shininess);
    }

    private void loadTextures(GL2 gl) {
        earthTexture = tryLoadTextureFromResources(gl, "/textures/earth.jpg");
        if (earthTexture == null) {
            System.err.println("WARNING: Could not load earth.jpg texture!");
        }
    }

    private Texture tryLoadTextureFromResources(GL2 gl, String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                System.err.println("Failed to load texture: " + resourcePath + " (resource not found)");
                return null;
            }
            
            String lower = resourcePath.toLowerCase();
            String ext = lower.endsWith(".png") ? "png" 
                       : lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "jpg" 
                       : null;
            
            if (ext == null) {
                System.err.println("Failed to load texture: " + resourcePath + " (unsupported extension)");
                return null;
            }
            
            Texture texture = TextureIO.newTexture(in, true, ext);
            // Ensure texture coordinates wrap horizontally/vertically so texture matrix translation doesn't clamp
            texture.bind(gl);
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_REPEAT);
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_T, GL2.GL_REPEAT);
            // Use decent filtering (mipmaps are generated by TextureIO when 'true' above)
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR_MIPMAP_LINEAR);
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
            // Sanity check: equirectangular textures should be 2:1 aspect ratio
            try {
                int imgW = texture.getImageWidth();
                int imgH = texture.getImageHeight();
                if (imgW > 0 && imgH > 0) {
                    double ratio = (double) imgW / (double) imgH;
                    if (Math.abs(ratio - 2.0) > 0.1) {
                        System.err.printf("WARNING: Texture %s is %dx%d (aspect %.2f). For correct globe mapping use an equirectangular 2:1 map.%n",
                                resourcePath, imgW, imgH, ratio);
                    }
                }
            } catch (Throwable t) {
                // If querying image size isn't supported, ignore
            }
            System.out.println("Successfully loaded texture: " + resourcePath);
            return texture;
        } catch (IOException e) {
            System.err.println("Failed to load texture: " + resourcePath + " - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void updateAnimation() {
        long now = System.nanoTime();
        long delta = now - lastNanos;
        lastNanos = now;
        
        double dt = delta / 1_000_000_000.0; // seconds
        earthSpinDeg = (float) ((earthSpinDeg + (360.0 * dt / EARTH_ROTATION_PERIOD_SEC)) % 360.0);
    }

    private void attachInputHandlers(GLCanvas canvas) {
        // Keyboard controls
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT -> rotationYDeg -= 5.0f;
                    case KeyEvent.VK_RIGHT -> rotationYDeg += 5.0f;
                    case KeyEvent.VK_UP -> rotationXDeg -= 5.0f;
                    case KeyEvent.VK_DOWN -> rotationXDeg += 5.0f;
                    case KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS -> cameraZoom += 0.5f;
                    case KeyEvent.VK_MINUS -> cameraZoom -= 0.5f;
                    case KeyEvent.VK_R -> {
                        rotationXDeg = 20.0f;
                        rotationYDeg = -30.0f;
                        cameraZoom = -10.0f;
                        earthSpinDeg = 0.0f;
                    }
                    default -> {}
                }
                canvas.display();
            }
        });

        // Mouse drag to rotate
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
                
                rotationYDeg += dx * 0.5f;
                rotationXDeg += dy * 0.5f;
                
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                canvas.display();
            }
        });

        // Mouse wheel to zoom
        canvas.addMouseWheelListener((MouseWheelEvent e) -> {
            cameraZoom += e.getWheelRotation() * 0.5f;
            canvas.display();
        });
    }

    public static void main(String[] args) {
        GLProfile profile = GLProfile.get(GLProfile.GL2);
        GLCapabilities capabilities = new GLCapabilities(profile);
        capabilities.setDepthBits(24);

        GLCanvas canvas = new GLCanvas(capabilities);
        DrawEarth renderer = new DrawEarth();
        canvas.addGLEventListener(renderer);
        renderer.attachInputHandlers(canvas);

        Frame frame = new Frame("Draw Earth with Texture");
        frame.add(canvas);
        frame.setSize(1200, 800);
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
        
        System.out.println("Controls:");
        System.out.println("  Arrow keys: Rotate view");
        System.out.println("  +/- keys: Zoom in/out");
        System.out.println("  A/D keys: Adjust texture offset (rotate texture left/right)");
        System.out.println("  R key: Reset view");
        System.out.println("  Mouse drag: Rotate view");
        System.out.println("  Mouse wheel: Zoom");
    }
}
