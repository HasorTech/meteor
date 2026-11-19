import {createApp} from 'vue';
import App from './App.vue';
import {savedLocale, setLocale, t} from './i18n';
import './style.css';

async function start() {
    try {
        await setLocale(savedLocale());
    } catch {
        const retry = document.createElement('button');
        retry.type = 'button';
        retry.textContent = 'Could not load the console. Retry';
        retry.addEventListener('click', start, {once: true});
        document.querySelector('#app').replaceChildren(retry);
        return;
    }
    const app = createApp(App);
    app.config.globalProperties.$t = t;
    app.mount('#app');
}

start();
