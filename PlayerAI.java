import java.util.*;
import java.awt.Point;

public class PlayerAI extends ClientAI {

	/////////////////////////
	boolean isBoardValid = false;
	List<Turret> turrets;
	Gameboard gameboard;
	Opponent opponent;
	Player player;

	Map<Integer, Turret> turretsByX;
	Map<Integer, Turret> turretsByY;

	List<DangerDirection> dangerDirections;
	List<Direction>goTo;
	List<Direction> safe;

	int[] xMultipliers = {0, 0, 1, -1};
	int[] yMultipliers = {-1, 1, 0, 0};

	List<Direction> directions;
	Move[] moves = {Move.FACE_UP, Move.FACE_DOWN, Move.FACE_RIGHT, Move.FACE_LEFT, Move.FORWARD};
	Direction facing;
	int[][] bulletNext;
	final int UP_INDEX = 0, DOWN_INDEX = 1, RIGHT_INDEX = 2, LEFT_INDEX = 3;
	int targetX, targetY;
	boolean firstSight = true;
	/////////////////////////

	int[][] board;

	static final int MYTOWER = -8;
	static final int MYWALL = -7;
	static final int MYINRANGE = -4;
	static final int MYINVALIDRANGE = -1;


	///////////////////////

	public PlayerAI() {
		//Write your initialization here  
		turrets = new ArrayList<Turret>();

		turretsByX = new HashMap();
		turretsByY = new HashMap();

		safe = new ArrayList<Direction>();
		goTo = new ArrayList<Direction>();
		directions = new ArrayList<Direction>();

		dangerDirections = new ArrayList<DangerDirection>(); 

		targetX = 4;
		targetY = 6;

		directions.add(Direction.UP);
		directions.add(Direction.DOWN);
		directions.add(Direction.RIGHT);
		directions.add(Direction.LEFT);

		dangerDirections.add(new DangerDirection(Direction.DOWN, UP_INDEX));
		dangerDirections.add(new DangerDirection(Direction.UP, DOWN_INDEX));
		dangerDirections.add(new DangerDirection(Direction.LEFT, RIGHT_INDEX));
		dangerDirections.add(new DangerDirection(Direction.RIGHT, LEFT_INDEX));

	}


	@Override
	public Move getMove(Gameboard gameboard, Opponent opponent, Player player) throws NoItemException, MapOutOfBoundsException {

		this.gameboard = gameboard;
		this.player = player;
		this.opponent = opponent;

		if (gameboard.getCurrentTurnNumber() <= 0) {

			board = new int[gameboard.getHeight()][gameboard.getWidth()];
			player.getDirection();
			int distanceToNearestTurret = 999;

			for (Turret e : gameboard.getTurrets()) {
				board[e.getY()][e.getX()] = MYTOWER;
			}
			for (Wall e : gameboard.getWalls()) {
				board[e.getY()][e.getX()] = MYWALL;
			}

		}
		if(!isBoardValid){
			updateGrid();
			populateTowerRanges();
			isBoardValid = true;
		}

		List<Bullet> bullets = gameboard.getBullets();

		fillGoTo();

		for(Direction direction : goTo){
			System.out.println("Direction : " + direction);
		}

		boolean immediateDanger = false;

		bulletNext = new int[gameboard.getWidth()][gameboard.getHeight()];

		for(Bullet bullet : bullets){//Check for any bullets that will kill us if we don't move(maximum urgency)
			bulletNext[getNextX(bullet.getX(), directions.indexOf(bullet.direction))][getNextY(bullet.getY(), directions.indexOf(bullet.direction))] = 1;
			if(checkXY(bullet)){
				immediateDanger = true;
			}
		}

		Collections.sort(dangerDirections);//Sort the directions that the threats are approaching us from to make sure that we go the safest way.

		for(int i = 0; i < dangerDirections.size(); i++){//Try and find best mix between the safest direction to move and the direction we want to move in.
			DangerDirection dangerDirection = dangerDirections.get(i);

			if(goTo.contains(directions.get(dangerDirection.index))){//if we want to go into the direction of this dangerDirection 

				if(directions.get(dangerDirection.index) == player.direction){//if we're already facing this way

					if(!dangerAhead(dangerDirection.index)){//make sure there are no threats ahead of us.
						return Move.FORWARD;
					}
					continue;
				}

				return moves[dangerDirection.index];//turn towards the direction we've decided to move in.
			}
		}

		//We now know there are no immediate Bullet Threats to our player
		Move toMake = Move.FORWARD;//We'll be comparing this with other proposed moves to find the best one to make.

		int x1 = player.getX(), x2 = player.getX(), x3 = player.getX(), x4 = player.getX();
		int y1 = player.getY(), y2 = player.getY(), y3 = player.getY(), y4 = player.getY();
		
		if(board[player.getY()][player.getX()] > 1 || board[player.getY()][player.getX()] == -1){//if we're in range of a turret, lets see if we can target one.
			for(int i = 0; i < 4; i++){//the coordinates refer to us branching out from our location to a max of 4 squares
				x1 = getNextX(x1, 0);
				x2 = getNextX(x2, 1);
				x3 = getNextX(x3, 2);
				x4 = getNextX(x4, 3);

				y1 = getNextY(y1, 0);
				y2 = getNextY(y2, 1);
				y3 = getNextY(y3, 2);
				y4 = getNextY(y4, 3);


				toMake = getBetterMove(toMake, canShootTurret(x1, y1));
				toMake = getBetterMove(toMake, canShootTurret(x2, y2));
				toMake = getBetterMove(toMake, canShootTurret(x3, y3));
				toMake = getBetterMove(toMake, canShootTurret(x4, y4));

				/*toMake = getBetterMove(toMake, canShootOpponent(x1, y1));//look for the opponent and make the best move
				toMake = getBetterMove(toMake, canShootOpponent(x2, y2));
				toMake = getBetterMove(toMake, canShootOpponent(x3, y2));
				toMake = getBetterMove(toMake, canShootOpponent(x4, y4));*/

			}

			if(toMake == Move.SHOOT){//If we're shooting we should re-evaluate our priorities next turn.
				isBoardValid = false;
				return toMake;
			}

			if(toMake != Move.FORWARD || goTo.size() > 0){//Only move forward if we have to (because we don't have a list of directions we want to go in this turn)
				return toMake;

			}
		}

		if(goTo.size() == 0){//If we didn't get any results from our BFS invalidate the board so we'll have a point to go to next turn.
			isBoardValid = false;
		}

		return moveToTarget();//Worst case scenario just move towards the target that BFS gave us.


	}
	private Move canShootOpponent(int x, int y){//Check if we can shoot our opponent and use a shield if he is a viable threat.
		if(opponent.getX() == x && opponent.getY() == y){
			if(!player.isShieldActive() && (opponent.getLaserCount() > 0 || opponent.getShieldCount() > 0) && player.getShieldCount() > 0){
				return Move.SHIELD;
			}
			else if(player.getLaserCount() > 0){
				return Move.LASER;
			}
			else if(player.direction == directionTo(x, y)){
				return Move.SHOOT;
			}
			else{
				return moves[directions.indexOf(directionTo(x, y))];
			}
		}
		return Move.FORWARD;
	}
	private Move moveToTarget(){//Move towards the target given in BFS
		for(int i = 0; i < 4; i++){
			if(goTo.contains(directions.get(i))){
				if(player.direction == directions.get(i)){
					if(!dangerAhead(i)){
						return Move.FORWARD;
					}
					continue;
				}
				return moves[i];
			}
		}
		return Move.SHOOT;
	}


	private boolean dangerAhead(int index){//Check if there's danger ahead of us in the direction given
		//Walls, incoming bullets and turrets are considered danger.

		int nextX = getNextX(player.getX(), index);
		int nextY = getNextY(player.getY(), index);

		if(board[nextY][nextX] > 1 || board[nextY][nextX] == -1){//If we're stepping into turret range branch out and look for turrets that could kill us.

			int x1 = nextX, x2 = nextX, x3 = nextX, x4 = nextX;
			int y1 = nextY, y2 = nextY, y3 = nextY, y4 = nextY;

			for(int i = 0; i < 4; i++){
				x1 = getNextX(x1, 0);
				x2 = getNextX(x2, 1);
				x3 = getNextX(x3, 2);
				x4 = getNextX(x4, 3);

				y1 = getNextY(y1, 0);
				y2 = getNextY(y2, 1);
				y3 = getNextY(y3, 2);
				y4 = getNextY(y4, 3);

				if(isTurretDangerous(x1, y1) || isTurretDangerous(x2, y2) || isTurretDangerous(x3, y3) || isTurretDangerous(x4, y4)){
					return true;    			
				}

			}
		}


		try {
			if(gameboard.isWallAtTile(nextX, nextY)){//check if there's a wall ahead of us.
				return true;
			}
		} catch (MapOutOfBoundsException e) {
			e.printStackTrace();
		} 

		if(bulletNext[nextX][nextY] != 0){//See if a bullet is moving into the square ahead of us.
			return true;
		}

		return false;
	}
	private Move getBetterMove(Move move1, Move move2){//Simply choose the best move of move1 and move2

		if(move1 == Move.SHIELD || move2 == Move.SHIELD){
			return Move.SHIELD;
		}
		if(move1 == Move.LASER || move2 == Move.LASER){
			return Move.LASER;
		}
		if(move1 == Move.SHOOT || move2 == Move.SHOOT){
			return Move.SHOOT;
		}
		else if(move1 != Move.FORWARD){
			return move1;
		}
		else if(move2 != Move.FORWARD){
			return move2;
		}
		else return move1;

	}
	private Move canShootTurret(int x, int y){//See if we have enough cooldown time on the turret to get in and kill it.

		if(board[y][x] == MYTOWER){//is there even a turret at this square?

			Turret turret = null;

			try {
				turret = gameboard.getTurretAtTile(x, y);
			} catch (NoItemException e) {
				e.printStackTrace();
			} catch (MapOutOfBoundsException e) {
				e.printStackTrace();
			}

			int fireTime = turret.getFireTime();
			int coolTime = turret.getCooldownTime();

			int remainder = (((gameboard.getCurrentTurnNumber()) % (fireTime + coolTime)));
			int remainingCooldown = 0;
			if(remainder > fireTime){
				remainingCooldown = coolTime - (remainder - fireTime);
			}
			else{
				remainingCooldown = -1;
			}

			if(remainingCooldown <= 1){
				if(player.getShieldCount() > 0 && !player.isShieldActive()){
					return Move.SHIELD;
				}
			}

			Direction towards =  directionTo(x, y);
			System.out.println("DirectionTo : " + towards);
			if(player.direction == towards && remainingCooldown >= 2){
				return Move.SHOOT;
			}
			else if(remainingCooldown >= 3){
				return moves[directions.indexOf(towards)]; 
			}

		}
		return Move.FORWARD;
	}

	private Direction directionTo(int x, int y){//gives the direction to give point
		if(player.getX() == x){
			if(player.getY() < y){
				return Direction.DOWN;
			}
			else if(player.getY() > y){
				return Direction.UP;
			}
		}
		if(player.getY() == y){
			if(player.getX() < x){
				return Direction.RIGHT;
			}
			else if(player.getX() > x){
				return Direction.LEFT;
			}
		}
		return Direction.UP;
	}
	private boolean isTurretDangerous(int x, int y){

		if(board[y][x] == MYTOWER){
			try {
				Turret turret = gameboard.getTurretAtTile(x, y);
				return turret.isFiringNextTurn();

			} catch (NoItemException e) {
				e.printStackTrace();
			} catch (MapOutOfBoundsException e) {
				e.printStackTrace();
			}

		}
		return false;
	}

	private int getNextX(int x, int index){//get nextX on the map including wrap
		int nextX = x + (1 * xMultipliers[index]);
		if(nextX == gameboard.getWidth()){
			nextX = 0;
		}
		if(nextX == -1){
			nextX = gameboard.getWidth()-1;
		}
		return nextX;
	}

	private int getNextY(int y, int index){//get nextY on the map including wrap
		int nextY = y + (1 * yMultipliers[index]);
		if(nextY == gameboard.getHeight()){
			nextY = 0;
		}
		if(nextY == -1){
			nextY = gameboard.getHeight()-1;
		}
		return nextY;
	}

	private boolean checkXY (Bullet bullet){//See if the bullet is two squares above, below, left or right of us.

		int bullX = bullet.getX(), bullY = bullet.getY();

		int playerX = player.getX(), playerY = player.getY();

		if(playerY == bullY || playerX == bullX){

			switch(bullet.getDirection()){   
			case UP:
				if(bullY <= 2 && playerY >= gameboard.getHeight() -2){
					dangerDirections.get(DOWN_INDEX).value++;
					return true;    
				}
				else if(bullY - playerY <= 2 && bullY - playerY > 0){
					dangerDirections.get(DOWN_INDEX).value++;
					return true;
				}
			case DOWN:
				if(bullY < gameboard.getHeight() - 2 && playerY < 2){
					dangerDirections.get(UP_INDEX).value++;
					return true;    
				}
				else if(playerY - bullY <= 2 && playerY - bullY > 0){
					dangerDirections.get(UP_INDEX).value++;
					return true;
				}
			case LEFT:
				if(bullX <= 2 && playerX >= gameboard.getWidth() -2){
					dangerDirections.get(RIGHT_INDEX).value++;
					return true;    
				}
				else if(bullX - playerX <= 2 && bullX - playerX > 0){
					dangerDirections.get(RIGHT_INDEX).value++;
					return true;
				}
			case RIGHT:
				if(bullX >= gameboard.getWidth() - 2 && playerX < 2){
					dangerDirections.get(LEFT_INDEX).value++;
					return true;    
				}
				else if(playerX - bullX <= 2 && playerX - bullX > 0){
					dangerDirections.get(LEFT_INDEX).value++;
					return true;
				}
			}

		}

		return false;

	}

	// Calls grid updates that don't dynamically affect movement.
	public void updateGrid() {
		for (int x = 0; x < board[0].length; x++) {
			for (int y = 0; y < board.length; y++)
				board[y][x] = 0;
		}
		populateTowers();
		populateWalls();
		populatePowerups();
	}

	// Populates the grid with towers, indicating spaces we don't go to.
	public void populateTowers() {
		for (Turret e : gameboard.getTurrets()) {
			if (e.isDead()){
				board[e.getY()][e.getX()] = -7; // Diff value from MYTOWER, but still below 0.
				continue;
			}
			board[e.getY()][e.getX()] = MYTOWER;
		}
	}

	// Populates the grid with walls, indicating spaces we don't go to.
	public void populateWalls() {
		for (Wall e : gameboard.getWalls()) {
			board[e.getY()][e.getX()] = MYWALL;
		}
	}

	// Adds to the arraylist the next optimal move to take.
	private void fillGoTo(){  
		goTo.clear();

		Move optimal = bfs(gameboard.getHeight(),gameboard.getWidth(),player.getY(),player.getX(),board);

		if (optimal == Move.FACE_UP) 
			goTo.add(Direction.UP);
		else if (optimal == Move.FACE_DOWN)
			goTo.add(Direction.DOWN);
		else if (optimal == Move.FACE_LEFT)
			goTo.add(Direction.LEFT);
		else if (optimal == Move.FACE_RIGHT)
			goTo.add(Direction.RIGHT);
	}
	// Finds the shortest path to an optimal cell, where an optimal cell is any cell that puts me in firing range of a tower,
	// or on a powerup.
	public Move bfs(int r, int c, int my_r, int my_c, int[][] grid){
		int rPos = my_r;
		int cPos = my_c;
		Node currNode = null;

		boolean[][]visited = new boolean[r][c];
		LinkedList<Node> nodes = new LinkedList<Node>();
		nodes.add(new Node(rPos,cPos, null, Move.NONE));
		visited[rPos][cPos] = true;
		Move direction = Move.NONE;
		int index = 0;
		while (true) {
			try{
				currNode = nodes.element();
			}catch (NoSuchElementException e){
				break;
			}
			rPos = currNode.rPos;
			cPos = currNode.cPos;

			if (grid[rPos][cPos] > 0)
				break;

			if (rPos-1 >= 0){
				if (grid[rPos-1][cPos] != MYWALL && grid[rPos-1][cPos] != MYTOWER && !visited[rPos-1][cPos]){
					currNode.children.add(new Node(rPos-1,cPos,currNode, Move.FACE_UP));
					visited[rPos-1][cPos] = true;
				}
			}
			else {
				if (grid[r-1][cPos] != MYWALL && grid[r-1][cPos] != MYTOWER && !visited[r-1][cPos]){
					currNode.children.add(new Node(r-1,cPos,currNode, Move.FACE_UP));
					visited[r-1][cPos] = true;
				}
			}    

			if (cPos-1 >= 0) {
				if (grid[rPos][cPos-1] != MYWALL && grid[rPos][cPos-1] != MYTOWER && !visited[rPos][cPos-1]){
					currNode.children.add(new Node(rPos,cPos-1,currNode, Move.FACE_LEFT));
					visited[rPos][cPos-1] = true;
				}
			}
			else {
				if (grid[rPos][c-1] != MYWALL && grid[rPos][c-1] != MYTOWER && !visited[rPos][c-1]){
					currNode.children.add(new Node(rPos,c-1,currNode, Move.FACE_LEFT));
					visited[rPos][c-1] = true;
				}
			}

			if (cPos+1 <= c-1) {
				if (grid[rPos][cPos+1] != MYWALL && grid[rPos][cPos+1] != MYTOWER && !visited[rPos][cPos+1]){
					currNode.children.add(new Node(rPos,cPos+1,currNode, Move.FACE_RIGHT));
					visited[rPos][cPos+1] = true;
				}
			}
			else {
				if (grid[rPos][0] != MYWALL && grid[rPos][0] != MYTOWER && !visited[rPos][0]){
					currNode.children.add(new Node(rPos,0,currNode, Move.FACE_RIGHT));
					visited[rPos][0] = true;
				}
			}

			if (rPos+1 <= r-1){
				if (grid[rPos+1][cPos] != MYWALL && grid[rPos+1][cPos] != MYTOWER && !visited[rPos+1][cPos]) {
					currNode.children.add(new Node(rPos+1,cPos,currNode, Move.FACE_DOWN));
					visited[rPos+1][cPos] = true;
				}
			}
			else {
				if (grid[0][cPos] != MYWALL && grid[0][cPos] != MYTOWER && !visited[0][cPos]) {
					currNode.children.add(new Node(0,cPos,currNode, Move.FACE_DOWN));
					visited[0][cPos] = true;
				}
			}
			nodes.remove();
			for (Node e : currNode.children) {
				nodes.add(e);
			}
		}

		while(true) {
			if (currNode.parent == null)
				break;
			else {
				direction = currNode.direction;
				currNode = currNode.parent;
			}
		}
		return direction;
	}

	// Every cell in the grid that has a powerup is now an optimal cell.
	public void populatePowerups() {
		for (PowerUp p : gameboard.getPowerUps()) {
			board[p.getY()][p.getX()] = 1;
		}
	}

	// Every cell in the grid that has puts me in firing range of a tower is now an optimal cell.
	public void populateTowerRanges() {
		int newpos;
		int overlap;
		boolean up = true;
		boolean down = true;
		boolean left = true;
		boolean right = true;

		for (Turret e : gameboard.getTurrets()) {
			if (!e.isDead()) {
				for (int i = 1; i < 4; i++) {
					int writeVal = 2;
					if(i > 4){
						writeVal = 3;
					}
					else if(e.getCooldownTime() < 4){
						writeVal = -1;
					}

					if (right) {
						newpos = e.getX()+i;
						if (newpos > gameboard.getWidth()-1)
							newpos = newpos - gameboard.getWidth();

						if (board[e.getY()][newpos] != MYWALL && board[e.getY()][newpos] != MYTOWER){
							board[e.getY()][newpos] = writeVal;
						}
						else
							right = false;
					}

					if (left) {
						newpos = e.getX()-i;
						if (newpos < 0)
							newpos = gameboard.getWidth()+newpos;

						if (board[e.getY()][newpos] != MYWALL && board[e.getY()][newpos] != MYTOWER)
							board[e.getY()][newpos] = writeVal;
						else
							left = false;
					}

					if (down) {
						newpos = e.getY()+i;
						if (newpos > gameboard.getHeight()-1)
							newpos = newpos - gameboard.getHeight();

						if (board[newpos][e.getX()] != MYWALL && board[newpos][e.getX()] != MYTOWER)
							board[newpos][e.getX()] = writeVal;
						else
							down = false;
					}

					if (up) {
						newpos = e.getY()-i;
						if (newpos < 0)
							newpos = gameboard.getHeight()+newpos;

						if (board[newpos][e.getX()] != MYWALL && board[newpos][e.getX()] != MYTOWER)
							board[newpos][e.getX()] = writeVal;
						else
							up = false;
					}
				}
				up = true;
				left = true;
				right = true;
				down = true;
			}
		}
	}

	class DangerDirection implements Comparable<DangerDirection>{// A simple Object that stores how may threats are coming from given direction.
		Direction direction;
		int value = 0;
		int index;
		public DangerDirection(Direction direction, int index){
			this.direction = direction;
			this.index = index;
		}
		@Override
		public int compareTo(DangerDirection other) {
			if(value == 0 && player.getDirection() == directions.get(index)){
				return -1;
			}
			return this.value - other.value;
		}
	}

	class Node {
		public Node(int r, int c, Node node, Move d) {
			rPos=r;
			cPos=c;
			parent = node;
			direction = d;
		}
		int rPos;
		int cPos;
		Node parent = null;
		Move direction;
		ArrayList<Node> children = new ArrayList<Node>();
	}

}
