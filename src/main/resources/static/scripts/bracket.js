
const start = document.querySelector("#start");
const quarterfinals = document.querySelector("#quarter-finals");
const semifinals = document.querySelector("#semi-finals");
const finals = document.querySelector("#finals");

function createMatch(player1, player2) {
    const match = document.createElement("article");
    match.className = "match";
    const p1 = document.createElement("div");
    p1.className = "player";
    if (!player1) p1.textContent = "TBD";
    else p1.textContent = player1.split(".").at(-1);
    const p2 = document.createElement("div");
    p2.className = "player";
    if (!player2) p2.textContent = "TBD";
    else p2.textContent = player2.split(".").at(-1);
    match.append(p1);
    match.append(p2);
    return match;
}

function logReceivedEvent(event) {

    if (event.detail.bracket == null) {
        return;
    }
    console.log(event.detail.bracket);
    const matches = event.detail.bracket.matches;
    const stages = [start, quarterfinals, semifinals, finals];

    for (let s = 0; s < stages.length; s++) {
        stages[s].replaceChildren();
        for (let i = 0; i < matches.length; i++) {
            if (matches[i].round == (s + 1) && matches[i].matchGroup == "WINNERS") {
                const newMatch = createMatch(matches[i].slot1, matches[i].slot2);
                stages[s].append(newMatch);
            }
        }
    }
}

window.addEventListener("competition-update", logReceivedEvent);