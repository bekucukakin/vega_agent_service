# Vega Agent Service

Cloud-based AI conflict resolution service for Vega VCS.

## Overview

Vega Agent Service is a standalone Spring Boot service that runs in the cloud and is responsible for resolving merge conflicts using AI. It communicates with Vega Core (CLI) via HTTP/REST.

## AI Merge Flow

See [AI-MERGE-FLOW.md](../AI-MERGE-FLOW.md) for detailed decision flow diagram.

## Architecture

```
┌─────────────┐         HTTP/REST          ┌──────────────────┐
│  Vega Core  │ ──────────────────────────> │ Vega Agent       │
│   (CLI)     │ <────────────────────────── │   Service        │
│             │    Conflict Resolution       │   (Cloud)        │
└─────────────┘                              └──────────────────┘
     │                                              │
     │                                              │
     │                                              ▼
     │                                    ┌──────────────────┐
     │                                    │  Google AI       │
     │                                    │  (Gemini API)    │
     └────────────────────────────────────┴──────────────────┘
```

## Responsibilities

### Vega Agent Service
- ✅ Receives conflict data from Vega Core
- ✅ Resolves conflicts using AI (Google Gemini)
- ✅ Returns proposed resolutions
- ❌ Does NOT access user filesystem
- ❌ Does NOT apply changes directly
- ❌ Does NOT require installation on user machines

### Vega Core (CLI)
- ✅ Detects conflicts
- ✅ Sends conflict data to Agent Service
- ✅ Manages user interaction
- ✅ Applies or rejects resolutions

## API Endpoints

### POST /api/agent/resolve

Resolves a merge conflict using AI.

**Request:**
```json
{
  "filePath": "src/main.java",
  "language": "java",
  "base": "original code",
  "ours": "our changes",
  "theirs": "their changes",
  "startLine": 10,
  "endLine": 20
}
```

**Response:**
```json
{
  "resolvedContent": "resolved code",
  "explanation": "Brief explanation",
  "warnings": [],
  "safeToApply": true
}
```

### GET /api/agent/health

Health check endpoint.

**Response:**
```json
{
  "service": "Vega Agent Service",
  "status": "READY",
  "available": true
}
```

## Configuration

### Environment Variables

- `GOOGLE_AI_API_KEY`: Google AI Studio API key (required)
- `VEGA_AGENT_SERVICE_URL`: Agent service URL (for Vega Core, default: `http://localhost:8084/api/agent`)

### Application Configuration

Edit `src/main/resources/application.yml`:

```yaml
server:
  port: 8084

google:
  ai:
    api-key: ${GOOGLE_AI_API_KEY:}
    timeout: 30000
```

## Running the Service

### Development

```bash
cd vega_agent_service
export GOOGLE_AI_API_KEY="your-api-key"
mvn spring-boot:run
```

### Production

```bash
mvn clean package
java -jar target/vega-agent-service-1.0-SNAPSHOT.jar
```

## Integration with Vega Core

Vega Core uses `AgentClientService` to communicate with Agent Service:

```java
AIConflictResolutionService aiService = AIConflictResolutionServiceFactory.createDefault();
AIResolution resolution = aiService.proposeResolution(conflictData);
```

## Service Boundaries

### ✅ What Agent Service Does
- Stateless conflict resolution
- AI-powered merge suggestions
- Returns structured responses

### ❌ What Agent Service Does NOT Do
- Access user filesystem
- Apply changes directly
- Handle user authentication (handled by user-service)
- Store conflict history
- Manage repository state

## Testing

### Unit Tests

```bash
mvn test
```

### Integration Test

Start the service and test with Vega Core:

```bash
# Terminal 1: Start Agent Service
cd vega_agent_service
export GOOGLE_AI_API_KEY="your-api-key"
mvn spring-boot:run

# Terminal 2: Test from Vega Core
cd VEGA
# Use vega merge --ai command (when implemented)
```

## Port Configuration

- **Agent Service**: Port 8084
- **Push Service**: Port 8082
- **Pull Service**: Port 8083
- **User Service**: Port 8085

## Current Design Choices & Limits

### Intentional Design Decisions

1. **Sequential Conflict Processing**
   - Conflicts are processed one at a time (not in parallel)
   - **Reason**: Prevents timeout issues and API rate limiting
   - **Trade-off**: Slower for many conflicts, but more reliable

2. **AI Advisory Only**
   - AI provides suggestions, user makes final decision
   - **Reason**: Safety and user control
   - **Trade-off**: Requires user interaction, but prevents unwanted changes

3. **No Persistent State**
   - Agent Service is stateless (request → response)
   - **Reason**: Scalability and simplicity
   - **Trade-off**: No history tracking, but easier to scale

4. **No Batch Processing**
   - Each conflict requires separate API call
   - **Reason**: Better error handling and partial success support
   - **Trade-off**: More API calls, but more resilient

### Recommended Limits

| Metric | Recommended Limit | Hard Limit | Notes |
|--------|-------------------|------------|-------|
| Conflicts per merge | 50 | 100 | More than 50 may cause timeout issues |
| Conflict size | 10KB | 100KB | Larger conflicts may reduce AI quality |
| Timeout per conflict | 60s | 60s | Fixed timeout per conflict |
| Concurrent requests | N/A | N/A | Sequential processing only |

### Performance Characteristics

- **Average resolution time**: 5-15 seconds per conflict
- **Timeout threshold**: 60 seconds per conflict
- **Success rate**: ~80-90% (depends on conflict complexity)
- **Unsafe rate**: ~5-10% (AI marks as unsafe)

### Scaling Considerations

**Current Architecture**:
- Single instance can handle ~10-20 conflicts per minute
- Sequential processing prevents overload
- Stateless design allows horizontal scaling

**Future Improvements** (not implemented):
- Batch processing for multiple conflicts
- Conflict resolution caching
- Rate limit management
- Load balancing

---

## Future Enhancements

- [ ] Support for multiple AI providers (Claude, Local LLMs)
- [ ] Deterministic conflict resolution (non-AI)
- [ ] Conflict resolution caching
- [ ] Batch conflict resolution
- [ ] Conflict resolution history/analytics

