const source = new EventSource("/events");
let podiumTimer;

source.addEventListener("update-competition", event => {

    const competitionData = JSON.parse(event.data);

    console.log("Competition data:", competitionData);
    console.log("First game:", competitionData.games[0]);

    console.table(competitionData.games[0].entries);

    window.dispatchEvent(new CustomEvent("competition-update", {
        detail: competitionData
    }));
    showPodium("Battle Complete");
    clearTimeout(podiumTimer);

podiumTimer = setTimeout(() => {
    showLeaderboard();
}, 5000);
});

source.onmessage = event => {
    const messageData = JSON.parse(event.data);

    console.log("Message data:", messageData);
};

source.onerror = () => {
    console.log("Connection lost; browser will retry automatically");
};

console.log("connected", source);
