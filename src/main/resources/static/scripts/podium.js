function showPodium(roundName) {

    const topThree = sort(players).slice(0, 3);
    document.getElementById("round-name")
        .textContent = roundName;

    const positions = ["first", "second", "third"];

    positions.forEach((position, index) => {

        const player = topThree[index];

        document.getElementById(position + "-name")
            .textContent = player ? player.bot : "---";

        document.getElementById(position + "-score")
            .textContent = player
                ? player.getPoints() + " pts"
                : "";
    });

    document.getElementById("leaderboard").hidden = true;

    document.querySelector(".subtitle").hidden = true;

    document.getElementById("round-results").hidden = false;
}

function showLeaderboard() {

    document.getElementById("round-results").hidden = true;

    document.getElementById("leaderboard").hidden = false;

    document.querySelector(".subtitle").hidden = false;
}