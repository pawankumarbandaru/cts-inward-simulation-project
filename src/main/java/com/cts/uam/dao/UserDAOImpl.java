package com.cts.uam.dao;

import java.time.LocalDateTime;
import java.util.Optional;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cts.uam.enums.UserStatus;
import com.cts.uam.model.User;
import com.cts.util.HibernateUtil;

public class UserDAOImpl implements UserDAO {

    private static final Logger LOG = LoggerFactory.getLogger(UserDAOImpl.class);

    private static final int AUTO_LOCK_MINUTES = 30;
    private static final int MAX_FAILED_ATTEMPTS = 5;

    @Override
    public Optional<User> findByUsername(String username) {

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            return session.createNativeQuery(
                    "SELECT * FROM cts_users WHERE username = :username",
                    User.class)
                    .setParameter("username", username)
                    .uniqueResultOptional();
        }
    }

    @Override
    public void incrementFailedAttempts(User user) {

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            session.beginTransaction();

            User managed = session.get(User.class, user.getId());

            if (managed == null) {
                session.getTransaction().rollback();
                return;
            }

            managed.setFailedAttempts(managed.getFailedAttempts() + 1);

            if (managed.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {

                managed.setLockedUntil(LocalDateTime.now().plusMinutes(AUTO_LOCK_MINUTES));
                managed.setStatus(UserStatus.LOCKED);

                LOG.info("User '{}' locked after {} failed attempts.",
                        managed.getUsername(),
                        managed.getFailedAttempts());
            }

            session.merge(managed);

            session.getTransaction().commit();
        }
    }

    @Override
    public void recordSuccessfulLogin(User user) {

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            session.beginTransaction();

            User managed = session.get(User.class, user.getId());

            if (managed == null) {
                session.getTransaction().rollback();
                return;
            }

            managed.setFailedAttempts(0);
            managed.setLockedUntil(null);
            managed.setLastLogin(LocalDateTime.now());

            if (managed.getStatus() == UserStatus.LOCKED) {
                managed.setStatus(UserStatus.ACTIVE);
            }

            session.merge(managed);

            session.getTransaction().commit();
        }
    }

    @Override
    public void updatePasswordHash(long userId, String newPasswordHash) {

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            session.beginTransaction();

            User user = session.get(User.class, userId);

            if (user == null) {
                session.getTransaction().rollback();
                return;
            }

            user.setPasswordHash(newPasswordHash);

            session.merge(user);

            session.getTransaction().commit();
        }
    }
}