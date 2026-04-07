# TemuBelajar

TemuBelajar is a learning-partner matching and video chat application. The project consists of an Elixir-based microservices backend and a Kotlin Multiplatform frontend (capable of running on Android, iOS, Desktop, and Web via WASM).

## Repository Structure

- `backend_elixir/` — Elixir backend consisting of multiple microservices:
  - `api_gateway` (Port 4000)
  - `auth_service` (Port 4001)
  - `user_service` (Port 4002)
  - `signaling_service` (Port 4003) - WebSocket Signaling
  - `matchmaking_service` (Port 4004)
  - `email_service` (Port 4005)
  - `social_service` (Port 4006)
- `frontend/Temu Belajar/` — Kotlin Multiplatform (KMP) client application targeting Android, iOS, Desktop, and Web.

---

## 🚀 Running the Backend (Elixir)

The backend is structured as a collection of Elixir microservices. A convenience script is provided to start them all at once.

### Prerequisites (Backend)
- Elixir & Mix installed natively, OR
- Docker & Docker Compose (optional for containerized run)

### Start all services natively
```bash
cd backend_elixir
./start_all.sh
```

### Start all services via Docker
```bash
cd backend_elixir
USE_DOCKER=true ./start_all.sh
```
*Note: Make sure you have a `.env` file correctly configured inside `backend_elixir/` if needed.*

Once running, the central **API Gateway** will be accessible at: `http://localhost:4000`.

---

## 📱 Running the Frontend (Kotlin Multiplatform)

The frontend uses Compose Multiplatform and can be run on multiple targets.

### Prerequisites (Frontend)
- Java JDK 11+
- Android Studio / IntelliJ IDEA with Kotlin Multiplatform plugins.

### Running from Terminal

Navigate to the frontend directory first:
```bash
cd "frontend/Temu Belajar"
```

**1. Desktop (JVM)**
```bash
./gradlew :composeApp:run
```

**2. Web (WASM)**
```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```
*(This will start a local Webpack dev server).*

**3. Android**
```bash
./gradlew :composeApp:assembleDebug
```
*(Or open the project in Android Studio, select the `composeApp` run configuration, and press **Run** to test on a connected device).*

**4. iOS**
iOS compilation requires a macOS machine with Xcode installed. If you are on macOS:
- Open `iosApp/iosApp.xcworkspace` in Xcode and hit Run, or
- Use the KMP plugin in Android Studio/IntelliJ.

---

## Maintenance & Testing
The `backend_elixir` directory also contains scripts for testing:
- `./run_tests.sh` - Run unit/integration tests
- `./e2e_test.sh` - Run end-to-end tests

*(Refer to individual services inside `backend_elixir/services/` for specific local configurations).*
