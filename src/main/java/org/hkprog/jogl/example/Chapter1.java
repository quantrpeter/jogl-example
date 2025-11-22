package org.hkprog.jogl.example;

import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.awt.TextRenderer;

import java.awt.Frame;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Demonstrates coordinate transformation between two reference frames:
 * O₁x₁y₁z₁ - Earth-centered inertial frame (地心惯性坐标系)
 * O₂x₂y₂z₂ - Satellite body-fixed frame (卫星本体坐标系)
 */
public class Chapter1 implements GLEventListener {

    private final GLU glu = new GLU();
    private TextRenderer textRenderer;

    // Camera control
    private float cameraRotX = 25.0f;
    private float cameraRotY = -35.0f;
    private float cameraZoom = -30.0f;

    // Mouse interaction
    private int lastMouseX;
    private int lastMouseY;
    private boolean isDragging = false;

    // Animation parameters for O₂ frame
    private float o2TranslationX = 0.0f;
    private float o2TranslationY = 0.0f;
    private float o2TranslationZ = 0.0f;
    private float o2RotationAngle = 0.0f; // rotation around Z-axis

    // Test point coordinates
    private float testPointX = 3.0f;
    private float testPointY = 2.0f;
    private float testPointZ = 1.5f;

    // Animation control
    private boolean animationEnabled = true;
    private long lastTime = System.nanoTime();

    // Display options
    private boolean showGrid = true;
    private boolean showTrajectory = true;
    
    // Visual parameters
    private static final float EARTH_RADIUS = 2.5f;
    private static final float SATELLITE_SIZE = 0.6f;
    private static final float ORBIT_RADIUS = 8.0f;

    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        gl.glClearColor(0.15f, 0.15f, 0.15f, 1.0f);
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glDepthFunc(GL2.GL_LEQUAL);
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl.glLineWidth(2.0f);

        // Enable anti-aliasing
        gl.glEnable(GL2.GL_LINE_SMOOTH);
        gl.glHint(GL2.GL_LINE_SMOOTH_HINT, GL2.GL_NICEST);

        // Initialize text renderer
        textRenderer = new TextRenderer(new Font("SansSerif", Font.BOLD, 16));
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        if (textRenderer != null) {
            textRenderer.dispose();
        }
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

        // Update animation
        if (animationEnabled) {
            updateAnimation();
        }

        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();

        // Apply camera transformations
        gl.glTranslatef(0.0f, 0.0f, cameraZoom);
        gl.glRotatef(cameraRotX, 1.0f, 0.0f, 0.0f);
        gl.glRotatef(cameraRotY, 0.0f, 1.0f, 0.0f);

        // Draw grid if enabled
        if (showGrid) {
            drawGrid(gl);
        }

        // Draw Earth at O₁ origin
        drawEarth(gl);
        
        // Draw O₁ inertial frame (Earth-centered)
        drawCoordinateFrame(gl, "O₁", 
            new float[]{1.0f, 0.0f, 0.0f}, // X - red
            new float[]{0.0f, 0.8f, 0.0f}, // Y - green
            new float[]{0.0f, 0.0f, 1.0f}, // Z - blue
            5.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        
        // Draw orbital path
        drawOrbit(gl, ORBIT_RADIUS);

        // Draw satellite at O₂ position
        gl.glPushMatrix();
        gl.glTranslatef(o2TranslationX, o2TranslationY, o2TranslationZ);
        gl.glRotatef(o2RotationAngle, 0.0f, 0.0f, 1.0f);
        
        // Draw satellite body
        drawSatellite(gl);

        // Draw O₂ satellite body-fixed frame
        drawCoordinateFrame(gl, "O₂", 
            new float[]{1.0f, 0.5f, 0.0f}, // X - orange
            new float[]{0.5f, 1.0f, 0.5f}, // Y - light green
            new float[]{0.5f, 0.5f, 1.0f}, // Z - light blue
            4.0f, 0.0f, 0.0f, 0.0f, 0.0f);

        // Draw test point in O₂ frame (local coordinates)
        drawPoint(gl, testPointX, testPointY, testPointZ, 
                  new float[]{1.0f, 0.0f, 1.0f, 1.0f}, 8.0f);

        gl.glPopMatrix();

        // Draw the same point as it appears in O₁ frame (world coordinates)
        float[] worldCoords = transformToO1Frame(testPointX, testPointY, testPointZ);
        drawPoint(gl, worldCoords[0], worldCoords[1], worldCoords[2], 
                  new float[]{0.8f, 0.0f, 0.8f, 0.5f}, 6.0f);

        // Draw trajectory line between the two representations
        if (showTrajectory) {
            drawTrajectoryLine(gl, 
                o2TranslationX + testPointX, o2TranslationY + testPointY, o2TranslationZ + testPointZ,
                worldCoords[0], worldCoords[1], worldCoords[2]);
        }

        // Draw 2D overlay with information
        drawOverlay(drawable, worldCoords);
    }

    private void updateAnimation() {
        long currentTime = System.nanoTime();
        float deltaTime = (currentTime - lastTime) / 1_000_000_000.0f;
        lastTime = currentTime;

        // Satellite orbital motion around Earth
        float orbitSpeed = 0.3f; // radians per second
        float angle = (currentTime / 1_000_000_000.0f) * orbitSpeed;

        o2TranslationX = ORBIT_RADIUS * (float) Math.cos(angle);
        o2TranslationY = ORBIT_RADIUS * (float) Math.sin(angle);
        o2TranslationZ = 0.0f;

        // Satellite attitude rotation (yaw)
        float rotationSpeed = 30.0f; // degrees per second
        o2RotationAngle += rotationSpeed * deltaTime;
        if (o2RotationAngle > 360.0f) {
            o2RotationAngle -= 360.0f;
        }
    }

    private float[] transformToO1Frame(float x2, float y2, float z2) {
        // Transform point from O₂ frame to O₁ frame
        // Formula: P₁ = R * P₂ + T
        // where R is rotation matrix and T is translation vector

        double angleRad = Math.toRadians(o2RotationAngle);
        float cosA = (float) Math.cos(angleRad);
        float sinA = (float) Math.sin(angleRad);

        // Apply rotation (around Z-axis)
        float x1_rot = cosA * x2 - sinA * y2;
        float y1_rot = sinA * x2 + cosA * y2;
        float z1_rot = z2;

        // Apply translation
        float x1 = x1_rot + o2TranslationX;
        float y1 = y1_rot + o2TranslationY;
        float z1 = z1_rot + o2TranslationZ;

        return new float[]{x1, y1, z1};
    }

    private void drawCoordinateFrame(GL2 gl, String label, 
                                     float[] xColor, float[] yColor, float[] zColor,
                                     float axisLength, float ox, float oy, float oz, float rotation) {
        gl.glPushMatrix();

        // Draw origin sphere
        gl.glColor4f(0.2f, 0.2f, 0.2f, 0.8f);
        drawSphere(gl, 0.0f, 0.0f, 0.0f, 0.2f);

        // Draw X-axis (red-ish)
        gl.glColor3fv(xColor, 0);
        drawArrow(gl, 0, 0, 0, axisLength, 0, 0);

        // Draw Y-axis (green-ish)
        gl.glColor3fv(yColor, 0);
        drawArrow(gl, 0, 0, 0, 0, axisLength, 0);

        // Draw Z-axis (blue-ish)
        gl.glColor3fv(zColor, 0);
        drawArrow(gl, 0, 0, 0, 0, 0, axisLength);

        gl.glPopMatrix();
    }

    private void drawArrow(GL2 gl, float x1, float y1, float z1, 
                          float x2, float y2, float z2) {
        // Draw line
        gl.glBegin(GL2.GL_LINES);
        gl.glVertex3f(x1, y1, z1);
        gl.glVertex3f(x2, y2, z2);
        gl.glEnd();

        // Draw arrowhead
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
        
        if (length > 0) {
            gl.glPushMatrix();
            gl.glTranslatef(x2, y2, z2);
            
            // Rotate to point in direction of arrow
            float angleY = (float) Math.toDegrees(Math.atan2(dx, dz));
            float angleX = (float) Math.toDegrees(Math.asin(-dy / length));
            gl.glRotatef(angleY, 0, 1, 0);
            gl.glRotatef(angleX, 1, 0, 0);
            
            // Draw cone
            drawCone(gl, 0.15f, 0.4f);
            gl.glPopMatrix();
        }
    }

    private void drawCone(GL2 gl, float radius, float height) {
        int slices = 12;
        gl.glBegin(GL2.GL_TRIANGLE_FAN);
        gl.glVertex3f(0, 0, height); // tip
        for (int i = 0; i <= slices; i++) {
            float angle = (float) (2.0 * Math.PI * i / slices);
            float x = radius * (float) Math.cos(angle);
            float y = radius * (float) Math.sin(angle);
            gl.glVertex3f(x, y, 0);
        }
        gl.glEnd();
    }

    private void drawSphere(GL2 gl, float x, float y, float z, float radius) {
        gl.glPushMatrix();
        gl.glTranslatef(x, y, z);
        
        int slices = 20;
        int stacks = 20;
        
        for (int i = 0; i < stacks; i++) {
            float lat0 = (float) (Math.PI * (-0.5 + (float) i / stacks));
            float lat1 = (float) (Math.PI * (-0.5 + (float) (i + 1) / stacks));
            float z0 = radius * (float) Math.sin(lat0);
            float z1 = radius * (float) Math.sin(lat1);
            float r0 = radius * (float) Math.cos(lat0);
            float r1 = radius * (float) Math.cos(lat1);
            
            gl.glBegin(GL2.GL_QUAD_STRIP);
            for (int j = 0; j <= slices; j++) {
                float lng = (float) (2 * Math.PI * j / slices);
                float x0 = r0 * (float) Math.cos(lng);
                float y0 = r0 * (float) Math.sin(lng);
                float x1 = r1 * (float) Math.cos(lng);
                float y1 = r1 * (float) Math.sin(lng);
                
                gl.glVertex3f(x0, y0, z0);
                gl.glVertex3f(x1, y1, z1);
            }
            gl.glEnd();
        }
        
        gl.glPopMatrix();
    }
    
    private void drawEarth(GL2 gl) {
        // Draw Earth as a blue-green sphere
        gl.glPushMatrix();
        
        // Earth colors - blue ocean with green continents
        gl.glColor4f(0.2f, 0.4f, 0.7f, 0.9f);
        drawSphere(gl, 0.0f, 0.0f, 0.0f, EARTH_RADIUS);
        
        // Draw equatorial line
        gl.glColor4f(0.3f, 0.3f, 0.3f, 0.5f);
        gl.glLineWidth(1.0f);
        gl.glBegin(GL2.GL_LINE_LOOP);
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            float angle = (float) (2.0 * Math.PI * i / segments);
            float x = EARTH_RADIUS * (float) Math.cos(angle);
            float y = EARTH_RADIUS * (float) Math.sin(angle);
            gl.glVertex3f(x, y, 0.0f);
        }
        gl.glEnd();
        gl.glLineWidth(2.0f);
        
        gl.glPopMatrix();
    }
    
    private void drawSatellite(GL2 gl) {
        // Draw satellite body (box)
        gl.glColor4f(0.8f, 0.8f, 0.9f, 1.0f);
        drawBox(gl, 0.0f, 0.0f, 0.0f, SATELLITE_SIZE, SATELLITE_SIZE * 0.6f, SATELLITE_SIZE * 0.8f);
        
        // Draw solar panels
        gl.glColor4f(0.1f, 0.1f, 0.3f, 0.9f);
        float panelWidth = SATELLITE_SIZE * 2.0f;
        float panelHeight = SATELLITE_SIZE * 0.8f;
        float panelThickness = 0.05f;
        
        // Left panel
        drawBox(gl, -(SATELLITE_SIZE/2 + panelWidth/2), 0.0f, 0.0f, 
                panelWidth, panelHeight, panelThickness);
        
        // Right panel
        drawBox(gl, (SATELLITE_SIZE/2 + panelWidth/2), 0.0f, 0.0f, 
                panelWidth, panelHeight, panelThickness);
        
        // Draw antenna
        gl.glColor4f(0.9f, 0.9f, 0.9f, 1.0f);
        gl.glLineWidth(3.0f);
        gl.glBegin(GL2.GL_LINES);
        gl.glVertex3f(0.0f, 0.0f, SATELLITE_SIZE * 0.4f);
        gl.glVertex3f(0.0f, 0.0f, SATELLITE_SIZE * 1.2f);
        gl.glEnd();
        gl.glLineWidth(2.0f);
    }
    
    private void drawBox(GL2 gl, float cx, float cy, float cz, 
                         float width, float height, float depth) {
        float w = width / 2;
        float h = height / 2;
        float d = depth / 2;
        
        gl.glBegin(GL2.GL_QUADS);
        
        // Front face
        gl.glVertex3f(cx - w, cy - h, cz + d);
        gl.glVertex3f(cx + w, cy - h, cz + d);
        gl.glVertex3f(cx + w, cy + h, cz + d);
        gl.glVertex3f(cx - w, cy + h, cz + d);
        
        // Back face
        gl.glVertex3f(cx - w, cy - h, cz - d);
        gl.glVertex3f(cx - w, cy + h, cz - d);
        gl.glVertex3f(cx + w, cy + h, cz - d);
        gl.glVertex3f(cx + w, cy - h, cz - d);
        
        // Top face
        gl.glVertex3f(cx - w, cy + h, cz - d);
        gl.glVertex3f(cx - w, cy + h, cz + d);
        gl.glVertex3f(cx + w, cy + h, cz + d);
        gl.glVertex3f(cx + w, cy + h, cz - d);
        
        // Bottom face
        gl.glVertex3f(cx - w, cy - h, cz - d);
        gl.glVertex3f(cx + w, cy - h, cz - d);
        gl.glVertex3f(cx + w, cy - h, cz + d);
        gl.glVertex3f(cx - w, cy - h, cz + d);
        
        // Right face
        gl.glVertex3f(cx + w, cy - h, cz - d);
        gl.glVertex3f(cx + w, cy + h, cz - d);
        gl.glVertex3f(cx + w, cy + h, cz + d);
        gl.glVertex3f(cx + w, cy - h, cz + d);
        
        // Left face
        gl.glVertex3f(cx - w, cy - h, cz - d);
        gl.glVertex3f(cx - w, cy - h, cz + d);
        gl.glVertex3f(cx - w, cy + h, cz + d);
        gl.glVertex3f(cx - w, cy + h, cz - d);
        
        gl.glEnd();
    }
    
    private void drawOrbit(GL2 gl, float radius) {
        gl.glColor4f(0.5f, 0.5f, 0.5f, 0.4f);
        gl.glLineWidth(1.5f);
        gl.glBegin(GL2.GL_LINE_LOOP);
        int segments = 128;
        for (int i = 0; i < segments; i++) {
            float angle = (float) (2.0 * Math.PI * i / segments);
            float x = radius * (float) Math.cos(angle);
            float y = radius * (float) Math.sin(angle);
            gl.glVertex3f(x, y, 0.0f);
        }
        gl.glEnd();
        gl.glLineWidth(2.0f);
    }

    private void drawPoint(GL2 gl, float x, float y, float z, float[] color, float size) {
        gl.glPointSize(size);
        gl.glColor4fv(color, 0);
        gl.glBegin(GL2.GL_POINTS);
        gl.glVertex3f(x, y, z);
        gl.glEnd();

        // Draw small sphere for better visibility
        gl.glColor4fv(color, 0);
        drawSphere(gl, x, y, z, 0.25f);
    }

    private void drawTrajectoryLine(GL2 gl, float x1, float y1, float z1, 
                                    float x2, float y2, float z2) {
        gl.glLineStipple(2, (short) 0xAAAA);
        gl.glEnable(GL2.GL_LINE_STIPPLE);
        gl.glColor4f(0.7f, 0.0f, 0.7f, 0.4f);
        gl.glLineWidth(1.5f);
        
        gl.glBegin(GL2.GL_LINES);
        gl.glVertex3f(x1, y1, z1);
        gl.glVertex3f(x2, y2, z2);
        gl.glEnd();
        
        gl.glDisable(GL2.GL_LINE_STIPPLE);
        gl.glLineWidth(2.0f);
    }

    private void drawGrid(GL2 gl) {
        gl.glColor4f(0.8f, 0.8f, 0.8f, 0.5f);
        gl.glLineWidth(1.0f);
        
        float gridSize = 20.0f;
        int gridLines = 20;
        float step = gridSize * 2 / gridLines;
        
        gl.glBegin(GL2.GL_LINES);
        for (int i = 0; i <= gridLines; i++) {
            float pos = -gridSize + i * step;
            // Lines parallel to X-axis
            gl.glVertex3f(-gridSize, pos, 0);
            gl.glVertex3f(gridSize, pos, 0);
            // Lines parallel to Y-axis
            gl.glVertex3f(pos, -gridSize, 0);
            gl.glVertex3f(pos, gridSize, 0);
        }
        gl.glEnd();
        
        gl.glLineWidth(2.0f);
    }

    private void drawOverlay(GLAutoDrawable drawable, float[] worldCoords) {
        GL2 gl = drawable.getGL().getGL2();
        
        // Save the current matrices
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glOrtho(0, drawable.getSurfaceWidth(), 0, drawable.getSurfaceHeight(), -1, 1);
        
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        
        gl.glDisable(GL2.GL_DEPTH_TEST);
        
        textRenderer.beginRendering(drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
        textRenderer.setColor(1f, 1f, 1f, 1.0f);
        
        int y = drawable.getSurfaceHeight() - 30;
        int lineHeight = 35;
        
        textRenderer.setColor(1f, 1f, 1f, 1.0f);
        textRenderer.draw("Earth-Satellite Coordinate Transformation (地球-卫星坐标变换)", 20, y);
        y -= lineHeight * 1.5;
        
        textRenderer.setColor(0.2f, 0.4f, 0.9f, 1.0f);
        textRenderer.draw("Earth (地球) - O₁ Earth-Centered Inertial Frame (地心惯性坐标系)", 20, y);
        y -= lineHeight;
        
        textRenderer.setColor(0.8f, 0.8f, 0.9f, 1.0f);
        textRenderer.draw("Satellite (卫星) - O₂ Body-Fixed Frame (本体坐标系)", 20, y);
        y -= lineHeight * 1.5;
        
        textRenderer.setColor(1f, 1f, 1f, 1.0f);
        textRenderer.draw(String.format("Satellite Position: (%.2f, %.2f, %.2f) km", 
            o2TranslationX, o2TranslationY, o2TranslationZ), 20, y);
        y -= lineHeight;
        
        textRenderer.setColor(1f, 1f, 1f, 1.0f);
        textRenderer.draw(String.format("Satellite Attitude (Yaw): %.1f°", o2RotationAngle), 20, y);
        y -= lineHeight;
        
        textRenderer.setColor(1f, 1f, 1f, 1.0f);
        textRenderer.draw(String.format("Orbital Radius: %.2f km", ORBIT_RADIUS), 20, y);
        y -= lineHeight * 1.5;
        
        textRenderer.setColor(1.0f, 0.0f, 1.0f, 1.0f);
        textRenderer.draw(String.format("Point in O₂ frame: P₂ = (%.2f, %.2f, %.2f)", 
            testPointX, testPointY, testPointZ), 20, y);
        y -= lineHeight;
        
        textRenderer.setColor(0.8f, 0.0f, 0.8f, 1.0f);
        textRenderer.draw(String.format("Point in O₁ frame: P₁ = (%.2f, %.2f, %.2f)", 
            worldCoords[0], worldCoords[1], worldCoords[2]), 20, y);
        y -= lineHeight * 1.5;
        
        textRenderer.setColor(0.3f, 0.3f, 1f, 1.0f);
        textRenderer.draw("Transformation: P₁ = R(θ) * P₂ + T", 20, y);
        y -= lineHeight * 2;
        
        textRenderer.setColor(0.4f, 0.4f, 0.4f, 1.0f);
        textRenderer.draw("Controls: Mouse drag (rotate) | Wheel (zoom) | SPACE (pause) | R (reset) | G (grid) | T (trajectory)", 
            20, 30);
        
        textRenderer.endRendering();
        
        gl.glEnable(GL2.GL_DEPTH_TEST);
        
        // Restore matrices
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glViewport(0, 0, width, Math.max(height, 1));

        float aspect = (height == 0) ? 1.0f : (float) width / (float) height;
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        glu.gluPerspective(45.0f, aspect, 0.1f, 1000.0f);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    private void attachInputHandlers(GLCanvas canvas) {
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_SPACE -> {
                        animationEnabled = !animationEnabled;
                        if (animationEnabled) {
                            lastTime = System.nanoTime();
                        }
                    }
                    case KeyEvent.VK_R -> {
                        cameraRotX = 25.0f;
                        cameraRotY = -35.0f;
                        cameraZoom = -30.0f;
                        animationEnabled = true;
                        lastTime = System.nanoTime();
                    }
                    case KeyEvent.VK_G -> showGrid = !showGrid;
                    case KeyEvent.VK_T -> showTrajectory = !showTrajectory;
                    case KeyEvent.VK_LEFT -> cameraRotY -= 5.0f;
                    case KeyEvent.VK_RIGHT -> cameraRotY += 5.0f;
                    case KeyEvent.VK_UP -> cameraRotX -= 5.0f;
                    case KeyEvent.VK_DOWN -> cameraRotX += 5.0f;
                    case KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS -> cameraZoom += 2.0f;
                    case KeyEvent.VK_MINUS -> cameraZoom -= 2.0f;
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
                cameraRotY += dx * 0.5f;
                cameraRotX += dy * 0.5f;
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                canvas.display();
            }
        });

        canvas.addMouseWheelListener((MouseWheelEvent e) -> {
            cameraZoom += e.getWheelRotation() * 2.0f;
            canvas.display();
        });
    }

    public static void main(String[] args) {
        GLProfile profile = GLProfile.get(GLProfile.GL2);
        GLCapabilities capabilities = new GLCapabilities(profile);
        capabilities.setDepthBits(24);
        capabilities.setSampleBuffers(true);
        capabilities.setNumSamples(4);

        GLCanvas canvas = new GLCanvas(capabilities);
        Chapter1 demo = new Chapter1();
        canvas.addGLEventListener(demo);
        demo.attachInputHandlers(canvas);

        Frame frame = new Frame("Coordinate System Transformation - O₁ ↔ O₂");
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
    }
}
