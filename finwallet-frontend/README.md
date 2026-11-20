# FinWallet Frontend

FinWallet is a full-stack digital wallet application designed to demonstrate modern fintech software engineering practices.

This repository contains the frontend application, which provides the user interface for interacting with the FinWallet backend.

## Technology Stack

- **React** — Component-based user interface
- **TypeScript** — Type-safe frontend development
- **Vite** — Development server and build tooling
- **Java Spring Boot** — Backend REST API
- **PostgreSQL** — Backend database
- **JWT** — Authentication and protected API requests

## Features

The frontend is intended to support:

- User registration and login
- JWT-based authentication
- Wallet balance dashboard
- Money transfers between users
- Transaction history
- Deposit and withdrawal interfaces

_Feature availability depends on the current implementation._

## Project Structure

The frontend is organized around reusable React components, pages, and API integrations. Refer to the source directory for the current structure.

## Getting Started

### Prerequisites

- Node.js
- npm
- FinWallet backend running locally

### 1. Install dependencies

```bash
npm install
```

### 2. Configure environment variables

Create a `.env` file in the frontend project directory if required by the application.

Configure the backend API URL using the environment variable expected by the frontend source code.

For example, if the application uses `VITE_API_BASE_URL`:

```env
VITE_API_BASE_URL=http://localhost:8080
```

### 3. Start the development server

```bash
npm run dev
```

Open the local URL displayed in the terminal.

### 4. Build for production

```bash
npm run build
```

## Backend Integration

The frontend communicates with the FinWallet Spring Boot backend through REST APIs.

The backend is responsible for:

- User authentication and authorization
- Wallet management
- Financial transactions
- Ledger accounting
- Data persistence and financial integrity

Refer to the backend repository's `README.md` and `docs/` directory for detailed technical documentation.

## Development Principles

- Use TypeScript for maintainability and type safety.
- Keep UI components reusable.
- Separate API communication from presentation logic.
- Handle loading, error, and empty states appropriately.
- Never expose backend secrets or signing keys in frontend code.
- Treat backend responses as the authoritative source of financial data.

## Project Status

FinWallet is an actively developed fintech engineering portfolio project.

The application is intended for learning and demonstration, not production financial services.
