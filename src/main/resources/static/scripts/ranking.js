
class Player {

	constructor(inPlayer, inBot) {
		this.player = inPlayer;
		this.bot = inBot;
		this.placement = 1;
		this.points = 0;
		this.firsts = 0;
	}

	getPoints() {
		return this.points;
	}

	setStats(inPoints, inFirsts) {
		this.points = inPoints;
		this.firsts = inFirsts;
	}

	setPlacement(inPlace) {
		this.placement = inPlace;
	}

	getPlacement() {
		return this.placement;
	}
}

let players = [];

const playerRows = document.querySelector("#player-ids");
const rowByPlayer = new Map();
const refreshRate = 2000;

function sort(players) {
	let sorted = false;
	while (!sorted) {
		sorted = true;
		for (let i = 1; i < players.length; i++) {
			const prev = players[i - 1];
			const cur = players[i];
			if (cur.getPoints() > prev.getPoints()) {
				players[i] = prev;
				players[i - 1] = cur;
				sorted = false;
			}
		}
	}
	for (let i = 0; i < players.length; i++) {
		players[i].setPlacement(i + 1);
	}
	return players;
} 

function createCell(text, className) {
	const cell = document.createElement("td");
	if (className) {
		cell.className = className;
	}
	cell.textContent = text;
	return cell;
}

function renderPlayers() {
	const rankedPlayers = sort(players);

	const previousPositions = new Map(
		Array.from(playerRows.children, row => [row, row.getBoundingClientRect().top])
	);

	// Build one table row for each ranked player.
	const rows = rankedPlayers.map(player => {
		let row = rowByPlayer.get(player);
		if (!row) {
			row = document.createElement("tr");
			rowByPlayer.set(player, row);
		}
		row.replaceChildren();

		// Use the placement set by the sort function.
		row.append(createCell(String(player.placement)));

		// Create the player name cell using the playerProfile styles.
		const playerCell = document.createElement("td");
		
		const playerProfile = document.createElement("div");
		playerProfile.className = "playerProfile";

		const name = document.createElement("div");
		name.className = "title";
		name.textContent = player.player;
		playerProfile.append(name);
		playerCell.append(playerProfile);
		row.append(playerCell, createCell(player.bot));

		const pointsCell = document.createElement("td");
		pointsCell.className = "points-cell";
		const points = document.createElement("span");
		points.textContent = player.getPoints();
		pointsCell.append(points);
		row.append(pointsCell, createCell(player.firsts));
		return row;
	});
	
	playerRows.replaceChildren(...rows);

	if (!window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
		for (const row of rows) {
			const previousTop = previousPositions.get(row);
			if (previousTop === undefined) {
				continue;
			}

			const verticalOffset = previousTop - row.getBoundingClientRect().top;
			if (verticalOffset !== 0) {
				row.animate(
					[
						{ transform: `translateY(${verticalOffset}px)` },
						{ transform: "translateY(0)" }
					],
					{ duration: refreshRate / 2, easing: "ease" }
				);
			}
		}
	}
}

function updatePlayer(entry) {
	for (let player of players) {
			if (entry.id == player.bot) {
				player.setStats(entry.stats.score, entry.stats.firsts);
				return;
		}
	}
	let newPlayer = new Player("Placeholder", entry.id);
	newPlayer.setStats(entry.stats.score, entry.stats.firsts);
	players.push(newPlayer);
}

function logReceivedEvent(event) {
	for (let i = 0; i < event.detail.entries.length; i++) {
		let entry = event.detail.entries[i];
		updatePlayer(entry);
	}
}

window.addEventListener("competition-update", logReceivedEvent);

// Render once, then re-render every 2 seconds
renderPlayers();
setInterval(renderPlayers, refreshRate);