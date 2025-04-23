<p align="center">
  <a href="https://github.com/ForgeRock/ping-android-sdk">
    <img src="https://www.pingidentity.com/content/dam/picr/nav/Ping-Logo-2.svg" alt="Ping Identity Logo" width="200">
  </a>
  <hr/>
</p>

# Journey Module Concept

This document describes the plugins for the Journey Module, which integrate
with  [Orchestrate](https://github.com/ForgeRock/ping-android-sdk/tree/develop/foundation/orchestrate) Module .

## Overview

The Journey Module uses orchestrate module to customize and extend its functionality. Here's an overview of the
key plugins:

* **CustomHeader Module:** Injects required headers into Journey API requests.
* **RequestUrl Module:** Injects query parameters into the request URL (e.g., `ForceAuth`, `noSession`, `authType`).
* **Session Module:** Manages session tokens, using the Storage module for persistence.
* **NodeTransform Module:** Transforms API responses into the appropriate callback format.

## Plugin Details

### 1. CustomHeader Module

* **Functionality:** This module is responsible for adding any required headers to the API requests made by
  the Journey Module.
* **Purpose:** Ensures that all API requests contain the necessary headers for successful communication with the backend
  services.

### 2. RequestUrl Module

* **Functionality:** This module modifies the request URL by adding query parameters.
* **Purpose:** This allows for dynamic configuration of API requests. Examples of parameters that might be added
  include:
    * `ForceAuth`:  A boolean flag to force authentication.
    * `noSession`:  A boolean flag to indicate that a session should not be used.
    * `authType`:  A string specifying the type of authentication to use.
    * ...
* **Benefit:** Provides flexibility in controlling the behavior of API requests without changing the core logic.

### 3. Session Module

* **Functionality:** This module manages session tokens. It's responsible for obtaining, storing, and retrieving session
  tokens as needed.
* **Storage Module Dependency:** This module relies on a separate `Storage` module to persist the session tokens. This
  ensures that tokens can be stored across multiple requests or sessions.
* **Purpose:** Handles user authentication and session management, allowing the Journey Module to make authenticated
  requests.

### 4. NodeTransform Module

* **Functionality:** This module transforms the raw response received from an API into callbacks.
* **Benefit:** Decouples the API response format from the application's callback mapping.

### 5. OAuth Module

* **Functionality:** This module handles OAuth 2.0 authentication. It acquires access tokens, manages their lifecycle (
  refreshing when necessary), and allows configuration of token storage.
* **Purpose:** Enables the Journey Module to access protected resources by authenticating requests with OAuth 2.0.
* **Storage Configuration:** The module's design allows for configurable storage of the tokens, providing flexibility in
  how tokens are persisted (e.g., in secure storage, shared preferences, etc.).
* **Benefit:** Provides secure authentication and authorization for API requests, following industry best practices.