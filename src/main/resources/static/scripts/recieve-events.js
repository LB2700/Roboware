
const source = new EventSource("/events");

source.addEventListener("update-competition", event => {
    const competitionData = JSON.parse(event.data);
    const entries = competitionData.entries.sort((a, b) => b.stats.score - a.stats.score);

    console.log("League Entry List", entries);
});

source.addEventListener("update-tournament", (event) => {
    const tournamentData = JSON.parse(event.data);
    const entries = tournamentData.entries.sort((a, b) => b.stats.score - a.stats.score);

    console.log("Tournament Entry List", entries);
});

source.onmessage = event => {
    console.log(JSON.parse(event.data));
};

source.onerror = () => {
    console.log("Connection lost; browser will retry automatically");
};

console.log("connected", source);


