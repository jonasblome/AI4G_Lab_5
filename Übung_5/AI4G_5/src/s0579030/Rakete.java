package s0579030;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;

import lenz.htw.ai4g.ai.*;

public class Rakete extends AI {
	float screenRatio = (float) (info.getScene().getWidth() * 1.0 / info.getScene().getHeight());
	int widthDivision = 300;
	int heightDivision = (int) (widthDivision * (1 / screenRatio));
	Vector2D shipPosition = new Vector2D(info.getScene().getShopPosition(), 0);
	Point2D[] products = info.getScene().getRecyclingProducts();
	Point[] pearls = info.getScene().getPearl();
	Path2D[] obstacles = info.getScene().getObstacles();
	Vertex[] vertices = new Vertex[widthDivision * heightDivision];
	Rectangle[] streamsLeft = info.getScene().getStreamsToTheLeft();
	Rectangle[] streamsRight = info.getScene().getStreamsToTheRight();
	int leftLength = streamsLeft.length;
	int rightLength = streamsRight.length;
	Rectangle[] streams = new Rectangle[leftLength + rightLength];
	int pathProgress = 0;
	float errorMarginPearl = 1.1f;
	float errorMarginProduct = 1.2f;
	ArrayList<Vector2D> path = new ArrayList<>();
	ArrayList<ArrayList<Vector2D>> pearlPathsFromSurface = new ArrayList<>();
	ArrayList<ArrayList<Vector2D>> allPathsToNextPearl = new ArrayList<>();
	ArrayList<ArrayList<Vector2D>> productPathsFromSurface = new ArrayList<>();
	ArrayList<ArrayList<Vector2D>> allPathsToNextProduct = new ArrayList<>();
	int[] pathsSurfacePearlAirUsages = new int[pearls.length];
	int[] pathsToNextPearlAirUsages = new int[pearls.length - 1];
	int[] pathsSurfaceProductAirUsages = new int[products.length];
	int[] pathsToNextProductAirUsages = new int[pearls.length - 1];
	int state = 0;
	int itemsBought = 0;
	int searchRadius = 150;

	/**
	 * TODO
	 * @param info
	 */
	public Rakete(Info info) {
		super(info);
		enlistForTournament(579030, 577618);

		// Combine streams to the right and streams to the left into one array
		System.arraycopy(streamsLeft, 0, streams, 0, leftLength);
		System.arraycopy(streamsRight, 0, streams, leftLength, rightLength);

		// Sort pearls by x position
		pearls = (Point[]) sortElements(pearls, 2);

		// Remove all products that are inside of streams
		ArrayList<Point2D> reachableProducts = new ArrayList<>();
		reachableProducts.addAll(List.of(products));

		for(Point2D product: products) {
			for(Rectangle stream: streams) {
				if(stream.contains(product)) {
					reachableProducts.remove(product);
				}
			}
		}

		products = reachableProducts.toArray(new Point2D[0]);

		// Sort products by distance to starting position
		products = sortElements(products, 1);

		// Create path between pearls to follow
		setGrid();
		setNeighbours();
		setSurfacePearlPaths();
		setNextPearlPaths();
	}

	/**
	 * Sort elements with bubble sort
	 * Type 1: nearest elements to diver
	 * Type 2: left to right
	 */
	public Point2D[] sortElements(Point2D[] elements, int sortingType) {
		double elementComparator1;
		double elementComparator2;

		for(int i = 0; i < elements.length - 1; i++) {
			for(int j = 0; j < elements.length - i - 1; j++) {
				if(sortingType == 1) {
					Vector2D diverPosition = getDiverPosition();

					elementComparator1 = (int) getProductPosition(j).subtractVector(diverPosition).getLength();
					elementComparator2 = (int) getProductPosition(j + 1).subtractVector(diverPosition).getLength();
				}
				else {
					elementComparator1 = elements[j].getX();
					elementComparator2 = elements[j + 1].getX();
				}
				if(elementComparator1 > elementComparator2) {
					Point2D temp = elements[j];
					elements[j] = elements[j + 1];
					elements[j + 1] = temp;
				}
			}
		}

		return elements;
	}

	/**
	 * Set graph grid
	 */
	public void setGrid() {
		Vector2D pearlPosition = new Vector2D((float) pearls[0].getX(), (float) pearls[0].getY());

		// Scan grid for obstacles and set free tiles to true in freeSpace
		for (int x = 0; x < widthDivision; x++) {
			for (int y = 0; y < heightDivision; y++) {
				Rectangle2D currentTile = new Rectangle2D.Float();
				currentTile.setFrame(x * info.getScene().getWidth() / widthDivision, y * info.getScene().getHeight() / heightDivision, info.getScene().getWidth() / widthDivision, info.getScene().getHeight() / heightDivision);

				// Check each obstacle if it intersects with current tile
				for(Path2D obstacle : obstacles) {
					if (obstacle.intersects(currentTile)) {
						vertices[x + y * widthDivision] = null;
						break;
					} else {
						// If tile is free, create new vertex for graph in the middle of the tile
						Vector2D vertexPosition = new Vector2D((float) currentTile.getCenterX(), (float) currentTile.getCenterY());
						vertices[x + y * widthDivision] = new Vertex(vertexPosition, pearlPosition);
					}
				}
				for(Rectangle stream: streams) {
					if (stream.intersects(currentTile)) {
						vertices[x + y * widthDivision] = null;
						break;
					}
				}
			}
		}
	}

	/**
	 * Set graph vertex neighbours
	 */
	public void setNeighbours() {
		// Set the neighbours for each vertex
		for(int vertex = 0; vertex < vertices.length; vertex++) {
			Vertex leftNeighbour = vertices[vertex];
			Vertex rightNeighbour = vertices[vertex];
			Vertex upperNeighbour = vertices[vertex];
			Vertex lowerNeighbour = vertices[vertex];

			// Only check for the vertices in free space
			if(vertices[vertex] != null) {
				// Left
				if(vertex % widthDivision == 0) {
					leftNeighbour = null;
				}
				// Right
				if(vertex % widthDivision == widthDivision - 1) {
					rightNeighbour = null;

				}
				// Up
				if(vertex < widthDivision) {
					upperNeighbour = null;

				}
				// Down
				if(vertex > vertices.length - widthDivision - 1) {
					lowerNeighbour = null;
				}

				if(leftNeighbour == vertices[vertex]) {
					leftNeighbour = vertices[vertex - 1];
				}
				if(rightNeighbour == vertices[vertex]) {
					rightNeighbour = vertices[vertex + 1];
				}
				if(upperNeighbour == vertices[vertex]) {
					upperNeighbour = vertices[vertex - widthDivision];
				}
				if(lowerNeighbour == vertices[vertex]) {
					lowerNeighbour = vertices[vertex + widthDivision];
				}

				vertices[vertex].setNeighbour(0, leftNeighbour); // Left
				vertices[vertex].setNeighbour(1, rightNeighbour); // Right
				vertices[vertex].setNeighbour(2, upperNeighbour); // Up
				vertices[vertex].setNeighbour(3, lowerNeighbour); // Down
			}
		}
	}

	/**
	 * Set path from surface to pearls
	 */
	public void setSurfacePearlPaths() {
		System.out.println("Setting paths to surface...");
		for(int i = 0; i < pearls.length; i++) {
			Vector2D pointAbovePearl = getPearlPosition(i);
			pointAbovePearl.setY(14);
			Vector2D pearlPosition = getPearlPosition(i);

			// Find the shortest path from surface to current pearl and smooth it
			ArrayList<Vector2D> pathFromSurface = aStarPathFinding(pointAbovePearl, pearlPosition);
			pathFromSurface.add(pearlPosition);
			pathFromSurface = smoothPath(pathFromSurface);

			pathsSurfacePearlAirUsages[i] = calculateAirUsage(pathFromSurface, errorMarginPearl);
			pearlPathsFromSurface.add(i, pathFromSurface);
		}
	}

	/**
	 * Set path to succeeding pearl
	 */
	public void setNextPearlPaths() {
		System.out.println("Setting paths between pearls...");
		for(int i = 0; i < pearls.length - 1; i++) {
			Vector2D pearlPosition = getPearlPosition(i);
			Vector2D nextPearlPosition = getPearlPosition(i + 1);

			// Find the shortest path to next pearl and smooth it
			ArrayList<Vector2D> pathToNextPearl = aStarPathFinding(pearlPosition, nextPearlPosition);
			pathToNextPearl.add(nextPearlPosition);
			pathToNextPearl = smoothPath(pathToNextPearl);

			pathsToNextPearlAirUsages[i] = calculateAirUsage(pathToNextPearl, errorMarginPearl);
			allPathsToNextPearl.add(i, pathToNextPearl);
		}
	}

	/**
	 * Set path from surface to recycling products
	 * @param numOfProducts
	 */
	public void setSurfaceProductPaths(int numOfProducts) {
		System.out.println("Setting paths to surface...");
		for(int i = 0; i < numOfProducts; i++) {
			Vector2D pointAboveProduct = getProductPosition(i);
			pointAboveProduct.setY(14);
			Vector2D productPosition = getProductPosition(i);

			// Find the shortest path from surface to current pearl and smooth it
			ArrayList<Vector2D> pathFromSurface = aStarPathFinding(pointAboveProduct, productPosition);
			pathFromSurface.add(productPosition);
			pathFromSurface = smoothPath(pathFromSurface);

			pathsSurfaceProductAirUsages[i] = calculateAirUsage(pathFromSurface, errorMarginProduct);
			productPathsFromSurface.add(i, pathFromSurface);
		}
	}

	// Set path to succeeding product
	public void setNextProductPaths(int numOfProducts) {
		System.out.println("Setting paths between products...");
		for(int i = 0; i < numOfProducts - 1; i++) {
			Vector2D productPosition = getProductPosition(i);
			Vector2D nextProductPosition = getProductPosition(i + 1);

			// Find the shortest path to next pearl and smooth it
			ArrayList<Vector2D> pathToNextProduct = aStarPathFinding(productPosition, nextProductPosition);
			pathToNextProduct.add(nextProductPosition);
			pathToNextProduct = smoothPath(pathToNextProduct);

			pathsToNextProductAirUsages[i] = calculateAirUsage(pathToNextProduct, errorMarginProduct);
			allPathsToNextProduct.add(i, pathToNextProduct);
		}
	}

	// Buy as many items as possible with current money
	public ShoppingAction buyItem() {
		switch (itemsBought) {
			case 0:
				itemsBought++;
				System.out.println("Item 1 bought...");
				return new ShoppingAction(ShoppingItem.BALLOON_SET);
			case 1:
				itemsBought++;
				System.out.println("Item 2 bought...");
				return new ShoppingAction(ShoppingItem.STREAMLINED_WIG);
			case 2:
				itemsBought++;
				System.out.println("Item 3 bought...");
				return new ShoppingAction(ShoppingItem.CORNER_CUTTER);
			case 3:
				itemsBought++;
				System.out.println("Item 4 bought...");
				return new ShoppingAction(ShoppingItem.MOTORIZED_FLIPPERS);
			default:
				return null;
		}
	}

	// Sets path to the ship shop and collects bottles on the way
	public ArrayList<Vector2D> setPathToShip() {
		System.out.println("Setting path to ship...");
		ArrayList<Vector2D> pathToShip = new ArrayList<>();
		Vector2D diverPosition = getDiverPosition();
		ArrayList<Point2D> uncollectedProducts = new ArrayList<>();

		for(int rp = 0; rp < products.length; rp++) {
			Vector2D productPosition = getProductPosition(rp);

			int productShipDistance = (int) productPosition.subtractVector(shipPosition).getLength();
			int diverShipDistance = (int) diverPosition.subtractVector(shipPosition).getLength();
			int diverProductDistance = (int) diverPosition.subtractVector(productPosition).getLength();

			if(productShipDistance < diverShipDistance && diverProductDistance < diverShipDistance) {
				// Add product position to path to ship
				ArrayList<Vector2D> pathToProduct = aStarPathFinding(diverPosition, productPosition);
				pathToProduct.add(productPosition);
				pathToProduct = smoothPath(pathToProduct);
				pathToShip.addAll(pathToProduct);

				// Continue from current product position
				diverPosition = productPosition;
			}
			else {
				uncollectedProducts.add(products[rp]);
			}
		}

		// Adding path from last product back to ship
		ArrayList<Vector2D> bottleShipPath = aStarPathFinding(diverPosition, shipPosition);
		bottleShipPath = smoothPath(bottleShipPath);
		pathToShip.addAll(bottleShipPath);

		products = uncollectedProducts.toArray(new Point2D[0]);

		return pathToShip;
	}

	// Sets path to the ship shop and collects bottles on the way
	public ArrayList<Vector2D> setPathAroundShip(int radius) {
		System.out.println("Setting path around ship...");
		ArrayList<Vector2D> pathAroundShip = new ArrayList<>();
		products = sortElements(products, 1);
		int bottleSearchRadius = radius;
		ArrayList<Point2D> productsInRadius = new ArrayList<>();

		// Increase radius until there are at least 6 products gathered after searching it
		// After that the maximum radius is limited to 400 and maximum amount of collected products is 8
		while(productsInRadius.size() < 6 - (itemsBought * 2 + info.getMoney()) || (productsInRadius.size() < 8 - (itemsBought * 2 + info.getMoney()) && bottleSearchRadius < 400)) {
			productsInRadius = new ArrayList<>();
			bottleSearchRadius *= 1.05;

			// Check how many products are in the current radius
			for(int rp = 0; rp < products.length; rp++) {
				Vector2D productPosition = getProductPosition(rp);

				int productShipDistance = (int) productPosition.subtractVector(shipPosition).getLength();

				if (productShipDistance < bottleSearchRadius) {
					productsInRadius.add(products[rp]);
				}
			}
		}

		// Sort products in radius from left to right
		Point2D[] productsInRadiusSorted = new Point2D[productsInRadius.size()];

		for(int i = 0; i < productsInRadius.size(); i++) {
			productsInRadiusSorted[i] = productsInRadius.get(i);
		}

		productsInRadiusSorted = sortElements(productsInRadiusSorted, 2);

		// Copy products in radius into beginning of products array
		System.arraycopy(productsInRadiusSorted, 0, products, 0, productsInRadius.size());

		// Set product paths
		setSurfaceProductPaths(productsInRadius.size());
		setNextProductPaths(productsInRadius.size());
		System.out.println("There are " + productsInRadius.size() + " reachable products...");

		ArrayList<Vector2D> lastPartialPath = new ArrayList<>();
		for(int rp = 0; rp < productsInRadius.size();){
			int productsInPartialPath = 1;

			ArrayList<Vector2D> partialPath = new ArrayList<>();
			boolean partialPathDone = false;

			// Check all products in radius except the last one
			if(rp < productsInRadius.size() - 1) {
				// If we cannot reach the product, skip it
				if(info.getMaxAir() < calculateAirUsage(productPathsFromSurface.get(rp), errorMarginProduct) * 2) {
					System.out.println("Product " + (rp + 1) + " is too far away... (1)");
				}
				else {
					pathAroundShip.addAll(productPathsFromSurface.get(rp));
					partialPath.addAll(productPathsFromSurface.get(rp));
					partialPath.addAll(allPathsToNextProduct.get(rp));
					System.out.println("Product " + (rp + 1) + " collected");

					// Check all succeeding products in the radius if we can reach them
					// in the current partial path
					for (int j = rp; j < productsInRadius.size() - 2 && !partialPathDone; j++) {
						if (info.getMaxAir() > calculateAirUsage(partialPath, errorMarginProduct) + pathsSurfaceProductAirUsages[j + 1]) {
							// Add next product to partial path
							pathAroundShip.addAll(allPathsToNextProduct.get(j));
							partialPath.addAll(allPathsToNextProduct.get(j + 1));
							System.out.println("Product " + (rp + 1 + productsInPartialPath) + " collected");
							productsInPartialPath += 1;
						} else {
							// Add path to surface to partial path
							Collections.reverse(productPathsFromSurface.get(j));
							pathAroundShip.addAll(productPathsFromSurface.get(j));
							partialPathDone = true;
						}
						if(rp + productsInPartialPath > productsInRadius.size() - 3) {
							// Store last partial path
							lastPartialPath = partialPath;
						}
					}
				}
			}
			// Checking the path to the last product in the radius
			else {
				ArrayList<Vector2D> lastBottleShipPath = aStarPathFinding(getProductPosition(productsInRadius.size() - 1), shipPosition);
				lastBottleShipPath = smoothPath(lastBottleShipPath);

				// If we are at the next to last product
				if(!lastPartialPath.isEmpty()) {
					// If we can dive to the last product and back to the ship
					if(info.getMaxAir() > calculateAirUsage(lastPartialPath, errorMarginProduct) + pathsToNextProductAirUsages[productsInRadius.size() - 1] + calculateAirUsage(lastBottleShipPath, errorMarginProduct)) {
						pathAroundShip.addAll(allPathsToNextProduct.get(allPathsToNextProduct.size() - 1));
						pathAroundShip.addAll(lastBottleShipPath);
						System.out.println("Product " + (rp + 1) + " collected");
					}
					// If we can dive to the last product and back to surface
					else if(info.getMaxAir() > calculateAirUsage(lastPartialPath, errorMarginProduct) + pathsToNextProductAirUsages[productsInRadius.size() - 1] + pathsSurfaceProductAirUsages[productsInRadius.size() - 1]) {
						pathAroundShip.addAll(allPathsToNextProduct.get(allPathsToNextProduct.size() - 1));
						Collections.reverse(productPathsFromSurface.get(productPathsFromSurface.size() - 1));
						pathAroundShip.addAll(productPathsFromSurface.get(productPathsFromSurface.size() - 1));
						pathAroundShip.add(shipPosition);
						System.out.println("Product " + (rp + 1) + " collected");
					}
					// If we can't dive to the next product from the next to last one
					else {
						// Dive back to surface
						Collections.reverse(productPathsFromSurface.get(productPathsFromSurface.size() - 2));
						pathAroundShip.addAll(productPathsFromSurface.get(productPathsFromSurface.size() - 2));

						// If we can't dive to last product and back up
						if(info.getMaxAir() < calculateAirUsage(productPathsFromSurface.get(productsInRadius.size() - 1), errorMarginProduct) * 2) {
							System.out.println("Product " + (rp + 1) + " is too far away... (2)");
							pathAroundShip.add(shipPosition);
						}
						// If we can dive down to the last product and back to ship
						else if(info.getMaxAir() > calculateAirUsage(productPathsFromSurface.get(productsInRadius.size() - 1), errorMarginProduct) + calculateAirUsage(lastBottleShipPath, errorMarginProduct)){
							pathAroundShip.addAll(productPathsFromSurface.get(productPathsFromSurface.size() - 1));
							pathAroundShip.addAll(lastBottleShipPath);
							System.out.println("Product " + (rp + 1) + " collected");
						}
						// If we can only dive down to product and back to surface
						else {
							pathAroundShip.addAll(productPathsFromSurface.get(productPathsFromSurface.size() - 1));
							Collections.reverse(productPathsFromSurface.get(productPathsFromSurface.size() - 1));
							pathAroundShip.addAll(productPathsFromSurface.get(productPathsFromSurface.size() - 1));
							pathAroundShip.add(shipPosition);
							System.out.println("Product " + (rp + 1) + " collected");
						}
					}
				}
				// If we are above the last product
				else {
					// If we can't dive to the product and back to the ship
					if(info.getMaxAir() < calculateAirUsage(productPathsFromSurface.get(productsInRadius.size() - 1), errorMarginProduct) + calculateAirUsage(lastBottleShipPath, errorMarginProduct)) {
						// If we can't dive to the product and back to surface
						if(info.getMaxAir() < calculateAirUsage(productPathsFromSurface.get(productsInRadius.size() - 1), errorMarginProduct) * 2) {
							System.out.println("Product " + (rp + 1) + " is too far away... (3)");
						}
						// If we can dive to the last product and back to surface
						else {
							pathAroundShip.addAll(productPathsFromSurface.get(productPathsFromSurface.size() - 1));
							Collections.reverse(productPathsFromSurface.get(productPathsFromSurface.size() - 1));
							pathAroundShip.addAll(productPathsFromSurface.get((productPathsFromSurface.size() - 1)));
							pathAroundShip.add(shipPosition);
							System.out.println("Product " + (rp + 1) + " collected");
						}
					}
					// If we can dive to the product and back to the ship
					else {
						pathAroundShip.addAll(productPathsFromSurface.get(productPathsFromSurface.size() - 1));
						pathAroundShip.addAll(lastBottleShipPath);
						System.out.println("Product " + (rp + 1) + " collected");
					}
				}
			}

			// Continue from last product in final path
			rp += productsInPartialPath;
		}

		return pathAroundShip;
	}

	// Set path between all pearls
	public ArrayList<Vector2D> setPearlPath() {
		System.out.println("Setting path to pearls...");
		ArrayList<Vector2D> pearlPath = new ArrayList<>();
		ArrayList<Vector2D> lastPartialPath = new ArrayList<>();

		// Find ideal path with partial paths between pearls and paths to surface
		// Create partial paths between reachable pearls and add to final path
		for(int i = 0; i < pearls.length;) {
			// Set 1 because at least one pearl is in current partial path
			int pearlsInPartialPath = 1;

			// Check how many pearls are available from current pearl
			ArrayList<Vector2D> partialPath = new ArrayList<>();
			boolean partialPathDone = false;

			// Check path for all pearls except the last one
			if(i < pearls.length - 1) {
				// If we cannot reach the product, skip it
				if(info.getMaxAir() < calculateAirUsage(pearlPathsFromSurface.get(i), errorMarginPearl) * 2) {
					System.out.println("Pearl " + i + " is too far away...");
				}
				else {
					pearlPath.addAll(pearlPathsFromSurface.get(i));
					partialPath.addAll(pearlPathsFromSurface.get(i));
					partialPath.addAll(allPathsToNextPearl.get(i));

					// For all succeeding pearls check if they can be reached in the current partial path
					for (int j = i; j < pearls.length - 2 && !partialPathDone; j++) {
						if (info.getMaxAir() > calculateAirUsage(partialPath, errorMarginPearl) + pathsSurfacePearlAirUsages[j + 1]) {
							// Add next pearl to partial path
							pearlPath.addAll(allPathsToNextPearl.get(j));
							partialPath.addAll(allPathsToNextPearl.get(j + 1));
							pearlsInPartialPath += 1;
						} else {
							// Add path to surface to partial path
							Collections.reverse(pearlPathsFromSurface.get(j));
							pearlPath.addAll(pearlPathsFromSurface.get(j));
							partialPathDone = true;
						}
						// Store the last partial path
						if(i + pearlsInPartialPath > pearls.length - 3) {
							lastPartialPath = partialPath;
						}
					}
				}
			}
			else {
				// If we are at the next to last pearl
				if(!lastPartialPath.isEmpty()) {
					// If we can reach the last pearl without moving to surface
					if (info.getMaxAir() > calculateAirUsage(lastPartialPath, errorMarginPearl) + pathsToNextPearlAirUsages[pathsToNextPearlAirUsages.length - 1]) {
						pearlPath.addAll(allPathsToNextPearl.get(pearls.length - 2));
					}
					// If we have to dive to surface before diving for the last pearl
					else {
						Collections.reverse(pearlPathsFromSurface.get(pearlPathsFromSurface.size() - 2));
						pearlPath.addAll(pearlPathsFromSurface.get((pearlPathsFromSurface.size() - 2)));
						pearlPath.addAll(pearlPathsFromSurface.get((pearlPathsFromSurface.size() - 1)));
					}
				}
				// If we are above the last pearl, just dive straight down
				else {
					pearlPath.addAll(pearlPathsFromSurface.get((pearlPathsFromSurface.size() - 1)));
				}
			}

			// Continue from last pearl in final path
			i += pearlsInPartialPath;
		}

		return pearlPath;
	}

	// Skip all vertices in path that are unnecessary
	public ArrayList<Vector2D> smoothPath(ArrayList<Vector2D> path) {
		// Add the first vertex of the segment as a starting position
		ArrayList<Vector2D> smoothPath = new ArrayList<>();
		smoothPath.add(path.get(0));

		// Check each vertex in segment if a line to it would intersect with the obstacles
		for(int i = 1; i < path.size(); i++) {
			// Creating a line between the last good path vertex and the current segment vertex
			Line2D lineBetweenVertices = new Line2D.Float();
			lineBetweenVertices.setLine(smoothPath.get(smoothPath.size()-1).getX(), smoothPath.get(smoothPath.size()-1).getY(), path.get(i).getX(), path.get(i).getY());

			// Check each obstacle if it intersects with the line
			for(Path2D obstacle : obstacles) {
				if(intersects(lineBetweenVertices, obstacle)) {
					// If they intersect, add the previous vertex to the smooth path and check a new line
					smoothPath.add(path.get(i-1));
					break;
				}
			}
			// Check each stream if it intersects with the line
			for(Rectangle stream : streams) {
				if(intersects(lineBetweenVertices, stream)) {
					// If they intersect, add the previous vertex to the smooth path and check a new line
					smoothPath.add(path.get(i-1));
					break;
				}
			}
		}

		smoothPath.add(path.get(path.size() - 1));

		return smoothPath;
	}

	// Calculate air necessary to swim provided path
	public int calculateAirUsage(ArrayList<Vector2D> path, float errorMargin) {
		int pathLength = 0;
		int airUsageForPath;
		for(int i = 0; i < path.size() - 1; i++) {
			pathLength += path.get(i+1).subtractVector(path.get(i)).getLength();
		}
		airUsageForPath = pathLength / (int) Math.ceil(info.getMaxAcceleration());

		return (int) (airUsageForPath * errorMargin);
	}

	// Check if a line intersects with an obstacle
	public boolean intersects(Line2D line, Shape path) {
		Point2D start = null;
		Point2D point1 = null;
		Point2D point2 = null;
		for (PathIterator pi = path.getPathIterator(null); !pi.isDone(); pi.next()) {
			float[] coordinates = new float[6];
			switch (pi.currentSegment(coordinates)) {
				case PathIterator.SEG_MOVETO -> {
					point2 = new Point2D.Float(coordinates[0], coordinates[1]);
					point1 = null;
					start = (Point2D) point2.clone();
				}
				case PathIterator.SEG_LINETO -> {
					point1 = point2;
					point2 = new Point2D.Float(coordinates[0], coordinates[1]);
				}
				case PathIterator.SEG_CLOSE -> {
					point1 = point2;
					point2 = start;
				}
			}
			if (point1 != null) {
				Line2D segment = new Line2D.Float(point1, point2);
				if (segment.intersectsLine(line))
					return true;
			}
		}
		return false;
	}

	// Get diver position
	public Vector2D getDiverPosition() {
		return new Vector2D(info.getX(), info.getY());
	}

	// Get pearls position
	public Vector2D getPearlPosition(int pearl) {
		return new Vector2D((float) pearls[pearl].getX(), (float) pearls[pearl].getY());
	}

	// Get recycling product position
	public Vector2D getProductPosition(int product) {
		return new Vector2D((float) products[product].getX(), (float) products[product].getY());
	}

	@Override
	// Draw path
	public void drawDebugStuff(Graphics2D gfx) {
		// Drawing pearl path
		for(int i = 0; i < path.size()-1; i++) {
			gfx.setColor(Color.red);
			gfx.drawLine((int) path.get(i).getX(), (int) path.get(i).getY(), (int) path.get(i+1).getX(), (int) path.get(i+1).getY());
		}

		// Drawing grid
//		for(int i = 0; i < vertices.length; i++) {
//			if(vertices[i] != null) {
//				gfx.setColor(Color.blue);
//				gfx.drawOval((int) vertices[i].getLocation().getX(), (int) vertices[i].getLocation().getY(), 2, 2);
//			}
//		}
	}

	// Returns a path from the closest vertex to the starting position to the closest vertex to the next pearl position
	public ArrayList<Vector2D> aStarPathFinding(Vector2D startingPosition, Vector2D pearlPosition) {
		// Set all vertices of the graph to infinite distance with no previous node
		for(Vertex vertex: vertices) {
			if(vertex != null) {
				vertex.setDistanceToEnd(pearlPosition.subtractVector(startingPosition).getLength());
				vertex.setDistanceFromStartPosition(Double.POSITIVE_INFINITY);
				vertex.setPreviousVertex(null);
				vertex.setExplored(false);
			}
		}

		// Add a queue for the next node with the smallest distance
		Vertex currentVertex;
		PriorityQueue<Vertex> unexploredVertices = new PriorityQueue<>();

		// Find the closest vertex to start position and to pearl position
		double startToVertexDistance = Double.POSITIVE_INFINITY;
		Vertex closestVertexToStart = vertices[0];

		double pearlToVertexDistance = Double.POSITIVE_INFINITY;
		Vertex closestVertexToPearl = vertices[0];

		// Check distance to start and pearl for each vertex
		for (Vertex vertexToMeasure : vertices) {
			// Only check existing vertices
			if (vertexToMeasure != null) {
				double startDistanceToCurrentVertex = vertexToMeasure.getLocation().subtractVector(startingPosition).getLength();
				double pearlDistanceToCurrentVertex = vertexToMeasure.getLocation().subtractVector(pearlPosition).getLength();

				if (startDistanceToCurrentVertex < startToVertexDistance) {
					startToVertexDistance = startDistanceToCurrentVertex;
					closestVertexToStart = vertexToMeasure;
				}

				if (pearlDistanceToCurrentVertex < pearlToVertexDistance) {
					pearlToVertexDistance = pearlDistanceToCurrentVertex;
					closestVertexToPearl = vertexToMeasure;
				}
			}
		}

		// Add start node to the priority queue and set destination node
		unexploredVertices.add(closestVertexToStart);
		closestVertexToStart.setDistanceFromStartPosition(closestVertexToStart.getDistanceToEnd());
		closestVertexToStart.setPreviousVertex(null);
		Vertex destination = closestVertexToPearl;

		// For every neighbour of the current node update the distance
		// Set new previous node to current node
		while(!destination.getExplored()) {
			// Pull the nearest element out of the queue and get its neighbours
			currentVertex = unexploredVertices.poll();
			Vertex[] neighbours;

			if (currentVertex != null) {
				neighbours = currentVertex.getNeighbours();
			}
			else {
				return null;
			}

			// Look at all neighbours and check/update their distances
			for(Vertex neighbour : neighbours) {
				if(neighbour != null) {
					if (!neighbour.getExplored()) {
						// If the neighbour doesn't have a distance yet, set it and queue it
						if (neighbour.getDistanceFromStartPosition() == Double.POSITIVE_INFINITY) {
							neighbour.setDistanceFromStartPosition(currentVertex.getDistanceFromStartPosition() + 1 + currentVertex.getDistanceToEnd());
							neighbour.setPreviousVertex(currentVertex);
							unexploredVertices.add(neighbour);
						}
						// If it has a distance, just update it
						else {
							neighbour.setDistanceFromStartPosition(currentVertex.getDistanceFromStartPosition() + 1 + currentVertex.getDistanceToEnd());
							neighbour.setPreviousVertex(currentVertex);
						}
					}
				}
			}
			// Set current node to explored, so it won't be checked again
			currentVertex.setExplored(true);
		}

		// Backtrack the path from the destination to the start and return it as string
		currentVertex = destination;
		ArrayList<Vector2D> path = new ArrayList<>();

		while(currentVertex != null) {
			path.add(currentVertex.getLocation());
			currentVertex = currentVertex.getPreviousVertex();
		}

		// Make path from start to pearl
		Collections.reverse(path);

		return path;
	}

	@Override
	public String getName() {
		return "Rakete";
	}

	@Override
	public Color getPrimaryColor() {
		return Color.CYAN;
	}

	@Override
	public Color getSecondaryColor() {
		return Color.BLUE;
	}

	@Override
	public PlayerAction update() {
		Vector2D startVector = getDiverPosition();

		if(state == 0) {
			// Setting path to ship
			path = setPathToShip();
			pathProgress = 0;
			state = 1;
		}
		else if(state == 1) {
			// Swimming to ship
			if(pathProgress == path.size() - 1 && Math.abs(startVector.getX() - path.get(pathProgress).getX()) < 1 && Math.abs(startVector.getY() - path.get(pathProgress).getY()) < 1) {
				state = 2;
			}
		}
		else if(state == 2) {
			if(info.getMoney() > 1) {
				return buyItem();
			}
			else {
				state = 3;
			}
		}
		else if(state == 3) {
			// Setting path around ship
			path = setPathAroundShip(searchRadius);
			pathProgress = 0;
			state = 4;
		}
		else if(state == 4) {
			// Swimming around ship
			if(pathProgress == path.size() - 1 && Math.abs(startVector.getX() - path.get(pathProgress).getX()) < 1 && Math.abs(startVector.getY() - path.get(pathProgress).getY()) < 1) {
				state = 5;
			}
		}
		else if(state == 5) {
			if(info.getMoney() > 1) {
				return buyItem();
			}
			else {
				state = 6;
			}
		}
		else if(state == 6) {
			// Setting path between pearls
			path = setPearlPath();
			pathProgress = 0;
			state = 7;
		}
		else if(state == 7) {
			// Swimming between pearls
		}

		// Get next point in path ArrayList
		Vector2D seekVector = new Vector2D(path.get(pathProgress).getX(), path.get(pathProgress).getY());

		// Check if point on path was visited
		if(Math.abs(startVector.getX() - seekVector.getX()) < 1 && Math.abs(startVector.getY() - seekVector.getY()) < 1 && pathProgress < path.size() - 1) {
			pathProgress++;
		}

		// Seek pearl
		Vector2D seekDirection = seekVector.subtractVector(startVector);
		seekDirection = seekDirection.normalize();

		// Calculate direction radiant value
		float direction = (float) Math.atan2(seekDirection.getY(), seekDirection.getX());

		return new DivingAction(1, -direction);
	}
}
