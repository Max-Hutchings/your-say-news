package com.yoursay.social.service;

import com.yoursay.social.FollowStatusDto;
import com.yoursay.social.SocialService;
import com.yoursay.social.model.SocialFollow;
import com.yoursay.social.model.SocialFollowRepository;
import com.yoursay.user.YourSayUserDto;
import com.yoursay.user.YourSayUserService;
import com.yoursay.user.error.UserApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

import java.util.Set;

@ApplicationScoped
public class SocialServiceImpl implements SocialService {

    @Inject
    YourSayUserService userService;

    @Inject
    SocialFollowRepository followRepository;

    @Override
    @Transactional
    public FollowStatusDto follow(String followerEmail, long followedUserId) {
        long followerId = requireUserByEmail(followerEmail).id();
        requireUserById(followedUserId);
        if (followerId == followedUserId) {
            throw new BadRequestException("Users cannot follow themselves.");
        }
        if (followRepository.findPair(followerId, followedUserId) == null) {
            followRepository.save(new SocialFollow(followerId, followedUserId));
        }
        return status(followerId, followedUserId);
    }

    @Override
    @Transactional
    public FollowStatusDto unfollow(String followerEmail, long followedUserId) {
        long followerId = requireUserByEmail(followerEmail).id();
        requireUserById(followedUserId);
        followRepository.deletePair(followerId, followedUserId);
        return status(followerId, followedUserId);
    }

    @Override
    public FollowStatusDto getStatus(String viewerEmail, long userId) {
        long viewerId = requireUserByEmail(viewerEmail).id();
        requireUserById(userId);
        return status(viewerId, userId);
    }

    @Override
    public Set<Long> getFollowingUserIds(String viewerEmail) {
        long viewerId = requireUserByEmail(viewerEmail).id();
        return followRepository.followingIds(viewerId);
    }

    @Override
    public boolean isFollowing(long followerUserId, long followedUserId) {
        return followRepository.findPair(followerUserId, followedUserId) != null;
    }

    @Override
    public long countFollowers(long userId) {
        return followRepository.countFollowers(userId);
    }

    @Override
    public long countFollowing(long userId) {
        return followRepository.countFollowing(userId);
    }

    private FollowStatusDto status(long viewerId, long userId) {
        return new FollowStatusDto(
                userId,
                isFollowing(viewerId, userId),
                countFollowers(userId),
                countFollowing(userId));
    }

    private YourSayUserDto requireUserByEmail(String email) {
        YourSayUserDto user = userService.getByEmail(email);
        if (user == null) {
            throw UserApiException.notFoundForAuthenticatedSubject(email);
        }
        return user;
    }

    private YourSayUserDto requireUserById(long userId) {
        YourSayUserDto user = userService.getById(userId);
        if (user == null) {
            throw UserApiException.notFound(userId);
        }
        return user;
    }
}
