package s0579030;

public class Vertex implements Comparable<Vertex>{
	private Vector2D location;
	private Vertex previousVertex;
	private boolean explored;
	private double distanceToEnd;
	private double distanceFromStartPosition;
	private Vertex[] neighbours = new Vertex[4];

	public Vertex(Vector2D vertexLocation, Vector2D pearlLocation) {
		this.location = vertexLocation;
		explored = false;
		previousVertex = null;
		distanceToEnd = pearlLocation.subtractVector(vertexLocation).getLength();
		distanceFromStartPosition = Double.POSITIVE_INFINITY;
	}
	
	public boolean getExplored() {
		return explored;
	}
	
	public Vertex getPreviousVertex() {
		return previousVertex;
	}
	
	public Vector2D getLocation() {
		return location;
	}
	
	public double getDistanceToEnd() {
		return distanceToEnd;
	}
	
	public double getDistanceFromStartPosition() {
		return distanceFromStartPosition;
	}
	
	public void setExplored(boolean explored) {
		this.explored = explored;
	}
	
	public void setPreviousVertex(Vertex previousVertex) {
		this.previousVertex = previousVertex;
	}
	
	public void setLocation(Vector2D location) {
		this.location = location;
	}
	
	public void setDistanceToEnd(double distanceToEnd) {
		this.distanceToEnd = distanceToEnd;
	}
	
	public void setDistanceFromStartPosition(double distanceFromStartPosition) {
		this.distanceFromStartPosition = distanceFromStartPosition;
	}
	
	public int compareTo(Vertex o) {
		return Double.compare(distanceFromStartPosition, o.getDistanceFromStartPosition());
	}
	
	public void setNeighbour(int neighbourNumber, Vertex neighbour) {
		neighbours[neighbourNumber] = neighbour;
	}
	
	public Vertex[] getNeighbours() {
		return neighbours;
	}
}
