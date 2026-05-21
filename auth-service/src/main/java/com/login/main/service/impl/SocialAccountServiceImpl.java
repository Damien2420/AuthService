package com.login.main.service.impl;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.login.main.common.result.Result;
import com.login.main.entity.SocialAccount;
import com.login.main.repository.SocialAccountRepository;
import com.login.main.service.SocialAccountService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SocialAccountServiceImpl implements SocialAccountService {
    
    private final SocialAccountRepository socialAccountRepository;
    
    @Override
    public Result<SocialAccount> findByProviderID(String providerID) {
        Optional<SocialAccount> userOpt = socialAccountRepository.findByProviderID(providerID);
        if (userOpt.isPresent()) {
            return Result.success(userOpt.get());
        }
        return Result.fail("Social account not found");
    }
}
