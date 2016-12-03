package net.kothar.csview;
import java.io.IOException;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureIO;

public class OneTriangle {
    private static Texture icon;

	protected static void setup( GL2 gl2, int width, int height ) {
        gl2.glMatrixMode( GL2.GL_PROJECTION );
        gl2.glLoadIdentity();

        // coordinate system origin at lower left with width and height same as the window
        GLU glu = new GLU();
        glu.gluOrtho2D( 0.0f, width, 0.0f, height );

        gl2.glMatrixMode( GL2.GL_MODELVIEW );
        gl2.glLoadIdentity();

        gl2.glViewport( 0, 0, width, height );
        
        GLProfile glp = gl2.getGLProfile();
        try {
			TextureData textureData = TextureIO.newTextureData(glp, OneTriangle.class.getResourceAsStream("/icon.png"), false, ".png");
			icon = TextureIO.newTexture(textureData);
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

    protected static void render( GL2 gl2, int width, int height ) {
        gl2.glClear( GL.GL_COLOR_BUFFER_BIT );

        // Draw a textured quad
        gl2.glLoadIdentity();

        gl2.glColor3f(1,1,1);
        icon.enable(gl2);
        icon.bind(gl2);

        gl2.glBegin(GL2.GL_QUAD_STRIP);
        gl2.glTexCoord2f(0, 0); gl2.glVertex3f(100, 100, 0);
        gl2.glTexCoord2f(0, 1); gl2.glVertex3f(100, 400, 0);
        gl2.glTexCoord2f(1, 0); gl2.glVertex3f(400, 100, 0);
        gl2.glTexCoord2f(1, 1); gl2.glVertex3f(400, 400, 0);
        gl2.glEnd();
    }
}
