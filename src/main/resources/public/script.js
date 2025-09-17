async function fetchGreeting(name) {
    try {
        const response = await fetch(`/api/greeting?name=${name}`);
        if (!response.ok) {
            throw new Error('Error en la respuesta del servidor');
        }
        //const data = await response.json();   // for json response
        const data = await response.text(); // for text response
        const greetingDiv = document.getElementById('greeting');
        greetingDiv.innerHTML = `<p>${data}</p>`;   // for text response
        //greetingDiv.innerHTML = `<p>${data.mensaje}</p>`; // for json response
        greetingDiv.classList.add('visible');
    } catch (error) {
        console.error('Error fetching greeting:', error);
        const greetingDiv = document.getElementById('greeting');
        greetingDiv.innerHTML = '<p>Error al obtener el saludo. Intenta de nuevo.</p>';
        greetingDiv.classList.add('visible');
    }
}