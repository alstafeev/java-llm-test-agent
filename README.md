# LLM Test Agent

![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white) ![Spring](https://img.shields.io/badge/spring-%236DB33F.svg?style=for-the-badge&logo=spring&logoColor=white) ![Playwright](https://img.shields.io/badge/Playwright-45ba4b?style=for-the-badge&logo=Playwright&logoColor=white) 

**AI-powered agent for generating UI automated tests from TMS test cases.**

Uses LLM to analyze DOM and screenshots, execute browser actions step-by-step, and generate ready-to-run Playwright tests in Java or TypeScript.

## Features

- 🔄 **Step-by-step execution**: Each test step is analyzed and executed individually
- 📸 **Visual analysis**: Uses DOM snapshots and screenshots for accurate locator generation
- 🧠 **LLM-powered**: Leverages OpenAI, Gemini, or Ollama for intelligent test generation
- 🔗 **TMS integration**: Fetches test cases from Allure TestOps
- 🌐 **n8n compatible**: REST API for workflow automation
- ☕ **Java tests**: JUnit 5 + Playwright Java
- 📜 **TypeScript tests**: Playwright Test (coming soon)

## Architecture

```
llm-test-agent/
├── agent-core/          # Shared components (browser, models, TMS clients)
├── agent-java/          # Java JUnit 5 Playwright test generator
└── agent-typescript/    # TypeScript Playwright test generator (WIP)
```

```mermaid
sequenceDiagram
    participant TMS as Allure TestOps
    participant Agent as StepByStepOrchestrator
    participant LLM as LLM (OpenAI/Gemini/Ollama)
    participant Browser as Playwright
    
    TMS->>Agent: TestCase (steps[])
    Agent->>Browser: Open start URL
    
    loop Each Step
        Browser->>Agent: DOM + Screenshot
        Agent->>LLM: Analyze step
        LLM->>Agent: PlaywrightInstruction
        Agent->>Browser: Execute instruction
    end
    
    Agent->>LLM: Generate final test code
    LLM->>Agent: GeneratedTest.java
```

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- LLM API key (OpenAI, Gemini, or local Ollama)

### Installation

```bash
git clone https://github.com/alstafeev/llm-test-agent.git
cd llm-test-agent
mvn install
```

### Configuration

Set your LLM API key:
```bash
export OPENAI_API_KEY=your-key
# or
export GEMINI_API_KEY=your-key
```

### Usage

#### Shell (Interactive)
```bash
cd agent-java
mvn spring-boot:run

# In shell:
run-step-by-step --steps "Click login, Enter username" --url "https://example.com"
```

#### REST API
```bash
cd agent-java
mvn spring-boot:run

# Generate test
curl -X POST http://localhost:8080/api/v1/test-agent/generate \
  -H "Content-Type: application/json" \
  -d '{"title":"Login Test","steps":["Click login","Enter username"],"url":"https://example.com"}'
```

#### n8n Integration
Configure HTTP Request node:
- **URL**: `http://your-server:8080/api/v1/test-agent/webhook/n8n`
- **Method**: POST
- **Body**:
```json
{
  "title": "Test from n8n",
  "steps": ["Click login button", "Enter username admin"],
  "url": "https://target-site.com"
}
```

## Modules

| Module | Description |
|--------|-------------|
| `agent-core` | Shared components: browser management, models, TMS clients |
| `agent-java` | Java JUnit 5 Playwright test generator |
| `agent-typescript` | TypeScript Playwright test generator (placeholder) |

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/test-agent/health` | GET | Health check |
| `/api/v1/test-agent/generate` | POST | Generate and run test |
| `/api/v1/test-agent/analyze` | POST | Analyze steps (dry run) |
| `/api/v1/test-agent/webhook/n8n` | POST | n8n-compatible webhook |

## Configuration

| Property | Description | Default |
|----------|-------------|---------|
| `agent.url` | Target website URL | `https://example.com` |
| `agent.browser.headless` | Run browser headless | `true` |
| `agent.browser.timeout` | Browser timeout (ms) | `30000` |
| `agent.max-repair-attempts` | Self-repair retry limit | `3` |
| `agent.allure.project` | Allure TestOps project ID | - |
| `agent.allure.rql` | Allure RQL filter | - |

## License

Apache License 2.0

## Author

**Aleksei Stafeev** - [@alstafeev](https://github.com/alstafeev)
