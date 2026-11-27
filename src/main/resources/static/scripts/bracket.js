
const start = document.querySelector("#start");

function createMatch(player1, player2) {
    const match = document.createElement("article");
    match.className = "match";
    const p1 = document.createElement("div");
    p1.className = "player";
    p1.textContent = player1;
    const p2 = document.createElement("div");
    p2.className = "player";
    p2.textContent = player2;
    match.append(p1);
    match.append(p2);
    return match;
}

function logReceivedEvent(event) {
	console.log(event);
    if (event.detail.bracket == null) {
        return;
    }
    const matches = event.detail.bracket.matches;
    for (let i = 0; i < matches[i]; i++) {
        if (matches[i].round == 1) {
            const newMatch = createMatch(matches[i].slot1, matches[i].slot2);
            start.append(newMatch);
        }
    }
}

window.addEventListener("competition-update", logReceivedEvent);
