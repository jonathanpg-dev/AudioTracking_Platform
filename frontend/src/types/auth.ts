// Mirrors UserResponse.java.
export interface User {
  id: string
  username: string
  email: string
  createdAt: string
}

// Mirrors CurrentUserResponse.java -- what GET /users/me returns. Deliberately a separate shape
// from User/UserResponse (used by the generic user list/lookup endpoints) because these two
// extra fields are computed live on every call by walking the account's owned rows, which isn't
// something the app wants paid for on every row of a generic user list. See
// UserServiceImpl#getCurrentUser.
export interface CurrentUser extends User {
  // True only when this account owns nothing of its own (no Project/Asset/Collection/Tag/Client)
  // AND is linked as a client to at least one Project -- i.e. it exists purely to view
  // client-shared work. Naturally flips back to false the moment the account creates anything of
  // its own, with no separate "upgrade" step.
  isClientOnly: boolean
  // True whenever this account is linked as the client on at least one Project, regardless of
  // whether it also owns things of its own. Drives whether the "Client Projects" nav item shows
  // up for an otherwise-full-featured account.
  isLinkedAsClient: boolean
}

// Mirrors AuthResponse.java.
export interface AuthResponse {
  token: string
  tokenType: string
}

// Mirrors RegisterRequest.java.
export interface RegisterRequest {
  username: string
  email: string
  password: string
}

// Mirrors LoginRequest.java.
export interface LoginRequest {
  username: string
  password: string
}

// Mirrors GoogleLoginRequest.java.
export interface GoogleLoginRequest {
  idToken: string
}
