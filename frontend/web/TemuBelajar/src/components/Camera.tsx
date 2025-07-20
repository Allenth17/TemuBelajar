import { useRef, useState, useEffect, RefObject } from 'react';

const Camera = () => {
    const videoRef: RefObject<HTMLVideoElement> = useRef(null);
    const [error, setError] = useState<string | null>(null);
    const [isCameraOn, setIsCameraOn] = useState<boolean>(false);
    const [facingMode, setFacingMode] = useState<'user' | 'environment'>('user');
    const streamRef = useRef<MediaStream | null>(null);

  // Memulai kamera dengan konfigurasi
    const startCamera = async () => {
        try {
            const constraints: MediaStreamConstraints = {
                video: { 
                        facingMode: facingMode 
                    }
            };

            const stream = await navigator.mediaDevices.getUserMedia(constraints);
    
            if (videoRef.current) {
                videoRef.current.srcObject = stream;
                streamRef.current = stream;
                setIsCameraOn(true);
                setError(null);
            }
        } catch (err) {
            handleCameraError(err as Error);
        }
    };

  // Menangani error kamera
  const handleCameraError = (err: Error) => {
    console.error("Camera error:", err);
    let errorMessage = "Failed to access camera: ";
    
    if (err.name === 'NotAllowedError') {
      errorMessage += "Permission denied";
    } else if (err.name === 'NotFoundError') {
      errorMessage += "No camera available";
    } else if (err.name === 'NotReadableError') {
      errorMessage += "Camera is already in use";
    } else {
      errorMessage += err.message;
    }
    
    setError(errorMessage);
    setIsCameraOn(false);
  };

  // Menghentikan kamera
  const stopCamera = () => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
      setIsCameraOn(false);
    }
  };

  // Switch kamera (depan/belakang)
  const switchCamera = () => {
    stopCamera();
    setFacingMode(prev => prev === 'user' ? 'environment' : 'user');
  };

  // Cleanup saat komponen di-unmount
  useEffect(() => {
    return () => {
      stopCamera();
    };
  }, []);

  // Start kamera otomatis saat facingMode berubah
  useEffect(() => {
    if (facingMode && !isCameraOn) {
      startCamera();
    }
  }, [facingMode]);

  return (
    <div className="camera-container">
      <h2>Camera Access</h2>
      
      {error && <div className="error-message">{error}</div>}
      
      <div className="video-wrapper">
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          className="camera-feed"
          style={{ display: isCameraOn ? 'block' : 'none' }}
        />
        {!isCameraOn && <div className="camera-placeholder">Camera is off</div>}
      </div>
      
      <div className="camera-controls">
        {!isCameraOn ? (
          <button 
            onClick={startCamera} 
            className="camera-button start-button"
          >
            Start Camera
          </button>
        ) : (
          <>
            <button 
              onClick={stopCamera} 
              className="camera-button stop-button"
            >
              Stop Camera
            </button>
            <button 
              onClick={switchCamera} 
              className="camera-button switch-button"
            >
              Switch Camera
            </button>
          </>
        )}
      </div>
    </div>
  );
};

export default Camera;