
class Player {

	constructor(inPlayer, inBot) {
		this.player = inPlayer;
		this.bot = inBot;
		this.placement = 1;
		this.stats = {
			score: 0,
			firsts: 0,
			damage: 0
		}
	}

	setStats(inStats) {
		this.stats = {
			score: inStats.score,
			firsts: inStats.firsts,
			damage: (inStats.bulletDamage + inStats.ramDamage)
		}
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
			if (cur.stats.score > prev.stats.score) {
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

		row.append(createCell(player.player), createCell(player.bot), createCell(player.stats.score), createCell(player.stats.damage), createCell(player.stats.firsts));
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
				player.setStats(entry.stats);
				return;
		}
	}
	let newPlayer = new Player("Placeholder", entry.id);
	newPlayer.setStats(entry.stats);
	players.push(newPlayer);
}

function logReceivedEvent(event) {
	console.log(event.detail);
	for (let i = 0; i < event.detail.entries.length; i++) {
		let entry = event.detail.entries[i];
		updatePlayer(entry);
	}
}

window.addEventListener("competition-update", logReceivedEvent);

// Render once, then re-render every 2 seconds
renderPlayers();
setInterval(renderPlayers, refreshRate);