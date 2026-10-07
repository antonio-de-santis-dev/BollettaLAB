package it.bollettalab.platform;

import jakarta.servlet.http.HttpServletRequest;

public interface IdentityProvider {
  Identity authenticate(HttpServletRequest request);
}
