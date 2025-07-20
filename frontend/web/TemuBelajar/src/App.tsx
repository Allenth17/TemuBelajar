import Camera from './components/Camera';
import './App.css';

function App() {
  return (
    <div className="App">
      <header>
        <h1>Camera Access Demo</h1>
        <p>Contoh akses kamera dengan React + TypeScript</p>
      </header>
      <main>
        <Camera />
      </main>
      <footer>
        <p>Pastikan memberi izin saat browser meminta akses kamera</p>
      </footer>
    </div>
  );
}

export default App;
