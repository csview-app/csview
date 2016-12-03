package net.kothar.csview;

import static com.jogamp.opengl.GL.*;
import static com.jogamp.opengl.GL2.*;
import static com.jogamp.opengl.fixedfunc.GLMatrixFunc.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.GLPixelBuffer.GLPixelAttributes;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureIO;

public class OneTriangle {
	private static Texture icon;
	private static Texture text;

	protected static void setup(GL2 gl2, int width, int height) {
		gl2.glMatrixMode(GL_PROJECTION);
		gl2.glLoadIdentity();

		// coordinate system origin at lower left with width and height same as
		// the window
		GLU glu = new GLU();
		glu.gluOrtho2D(0.0f, width, 0.0f, height);

		gl2.glMatrixMode(GL_MODELVIEW);
		gl2.glLoadIdentity();

		gl2.glViewport(0, 0, width, height);

		try {
			InputStream stream = OneTriangle.class.getResourceAsStream("/icon.png");
			icon = TextureIO.newTexture(stream, false, ".png");
			
			Image image = new Image(Display.getDefault(), 100, 32);
			GC gc = new GC(image);
			gc.drawString("Hello", 32, 0, true);
			gc.dispose();
			
			int[] pixels = new int[32 * 100];
			image.getImageData().getPixels(0, 0, 100, pixels, 0);
			IntBuffer buffer = IntBuffer.wrap(pixels);
			
			TextureData textData = new TextureData(gl2.getGLProfile(), GL_RGBA, 100, 32, 0, 
					new GLPixelAttributes(GL_RGBA, GL_UNSIGNED_BYTE),
					false, false, false, buffer, null);
			text = new Texture(gl2, textData);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected static void render(GL2 gl2, int width, int height) {
		gl2.glClearColor(1, 1, 1, 1);
		gl2.glClear(GL_COLOR_BUFFER_BIT);

		gl2.glLoadIdentity();

		// Draw a textured quad
		icon.enable(gl2);
		icon.bind(gl2);
		gl2.glEnable(GL_BLEND);
		gl2.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		gl2.glBegin(GL_QUAD_STRIP);
		gl2.glTexCoord2f(0, 0);
		gl2.glVertex3f(100, 100, 0);
		gl2.glTexCoord2f(0, 1);
		gl2.glVertex3f(100, 400, 0);
		gl2.glTexCoord2f(1, 0);
		gl2.glVertex3f(400, 100, 0);
		gl2.glTexCoord2f(1, 1);
		gl2.glVertex3f(400, 400, 0);
		gl2.glEnd();

		icon.disable(gl2);
	}
}
