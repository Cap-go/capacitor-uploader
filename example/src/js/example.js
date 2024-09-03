import { Uploader } from '@capgo&#x2F;capacitor-uploader';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    Uploader.echo({ value: inputValue })
}
