
const source = new EventSource("/events");

source.addEventListener("update-competition", event => {
    const competitionData = JSON.parse(event.data);
    window.dispatchEvent(new CustomEvent("competition-update", {
        detail: competitionData
    }));
});

source.onmessage = event => {
    console.log(JSON.parse(event.data));
};

source.onerror = () => {
    console.log("Connection lost; browser will retry automatically");
};

console.log("connected", source);


