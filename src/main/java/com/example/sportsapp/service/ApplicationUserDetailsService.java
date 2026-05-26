package com.example.sportsapp.service;

import com.example.sportsapp.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
/**
 * Adapts application users to Spring Security's UserDetails contract.
 */
public class ApplicationUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    /**
     * Loads a user account by login name for authentication.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        com.example.sportsapp.entity.AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return new User(user.getUsername(), user.getPasswordHash(), AuthorityUtils.createAuthorityList("ROLE_USER"));
    }
}
