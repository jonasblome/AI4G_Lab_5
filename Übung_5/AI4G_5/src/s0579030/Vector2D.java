package s0579030;

import java.awt.Point;
import java.awt.geom.Point2D;

/**
 * 
 * @author milanatran
 *
 */
public class Vector2D {
	private float xCoord;
	private float yCoord;
	
	/**
	 * Creates a new 2D vector
	 * @param x
	 * @param y
	 */
	public Vector2D(float x, float y) {
		xCoord = x;
		yCoord = y;
	}
	
	public Vector2D() {
		xCoord = 0;
		yCoord = 0;
	}
	
	/**
	 * Sets the x and y value of the vector
	 * @param x
	 * @param y
	 */
	public void set(float x, float y) {
		xCoord = x;
		yCoord = y;
	}

	public void setY(int y) {
		yCoord = y;
	}

	public void setX(int x) {
		xCoord = x;
	}
	
	/**
	 * Returns the x value of the vector
	 * @return
	 */
	public float getX() {
		return xCoord;
	}
	
	/**
	 * Returns the y value of the vector
	 * @return
	 */
	public float getY() {
		return yCoord;
	}
	
	/**
	 * Adds another vector to this one and returns the result
	 * @param vector
	 * @return
	 */
	public Vector2D addVector(Vector2D vector) {
		return new Vector2D(vector.xCoord + xCoord, vector.yCoord + yCoord);
	}
	
	public Vector2D subtractVector(Vector2D vector) {
		return new Vector2D(xCoord - vector.xCoord, yCoord - vector.yCoord);
	}
	public Vector2D multiplyVector(float scalar) {
		return new Vector2D(xCoord * scalar, yCoord * scalar);
	}
	
	public boolean equals(Vector2D vector) {
		return vector.getX() == xCoord && vector.getY() == yCoord;
	}
	
	public Point2D convertToPoint() {
		Point2D point = new Point((int) xCoord, (int) yCoord);
		return point;
	}
	
	public Vector2D normalize() {
		double vectorLength = Math.sqrt(Math.pow(xCoord, 2) + Math.pow(yCoord, 2));
		return new Vector2D((float)(xCoord / vectorLength), (float) (yCoord / vectorLength));
	}
	
	public Vector2D rotate(float radians) {
		float x = (float) (Math.cos(radians) * xCoord - Math.sin(radians) * yCoord);
		float y = (float) (Math.sin(radians) * xCoord + Math.cos(radians) * yCoord);
		
		return new Vector2D(x, y);
	}
	
	public float getLength() {
		float length = (float) Math.sqrt(Math.pow(xCoord, 2) + Math.pow(yCoord, 2));
		
		return length;
	}
}