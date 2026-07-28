package com.aims.core.session;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link SessionStatus} 状态迁移规则测试。 */
class SessionStatusTest {

    @Nested
    class CreatedTransitions {
        @Test
        void createdToPlanning() {
            org.junit.jupiter.api.Assertions.assertTrue(
                    SessionStatus.CREATED.canTransitionTo(SessionStatus.PLANNING));
        }

        @Test
        void createdToCancelled() {
            org.junit.jupiter.api.Assertions.assertTrue(
                    SessionStatus.CREATED.canTransitionTo(SessionStatus.CANCELLED));
        }

        @Test
        void createdToFailed() {
            org.junit.jupiter.api.Assertions.assertTrue(
                    SessionStatus.CREATED.canTransitionTo(SessionStatus.FAILED));
        }

        @Test
        void createdNotToInProgress() {
            org.junit.jupiter.api.Assertions.assertFalse(
                    SessionStatus.CREATED.canTransitionTo(SessionStatus.IN_PROGRESS));
        }
    }

    @Nested
    class InProgressTransitions {
        @Test
        void inProgressToPaused() {
            org.junit.jupiter.api.Assertions.assertTrue(
                    SessionStatus.IN_PROGRESS.canTransitionTo(SessionStatus.PAUSED));
        }

        @Test
        void inProgressToCompleted() {
            org.junit.jupiter.api.Assertions.assertTrue(
                    SessionStatus.IN_PROGRESS.canTransitionTo(SessionStatus.COMPLETED));
        }

        @Test
        void inProgressToEvaluating() {
            org.junit.jupiter.api.Assertions.assertTrue(
                    SessionStatus.IN_PROGRESS.canTransitionTo(SessionStatus.EVALUATING));
        }

        @Test
        void inProgressToCancelled() {
            org.junit.jupiter.api.Assertions.assertTrue(
                    SessionStatus.IN_PROGRESS.canTransitionTo(SessionStatus.CANCELLED));
        }

        @Test
        void inProgressToFailed() {
            org.junit.jupiter.api.Assertions.assertTrue(
                    SessionStatus.IN_PROGRESS.canTransitionTo(SessionStatus.FAILED));
        }
    }

    @Nested
    class PausedTransitions {
        @Test
        void pausedToInProgress() {
            org.junit.jupiter.api.Assertions.assertTrue(
                    SessionStatus.PAUSED.canTransitionTo(SessionStatus.IN_PROGRESS));
        }

        @Test
        void pausedToCompleted() {
            org.junit.jupiter.api.Assertions.assertTrue(
                    SessionStatus.PAUSED.canTransitionTo(SessionStatus.COMPLETED));
        }

        @Test
        void pausedToCancelled() {
            org.junit.jupiter.api.Assertions.assertTrue(
                    SessionStatus.PAUSED.canTransitionTo(SessionStatus.CANCELLED));
        }
    }

    @Nested
    class TerminalStates {
        @Test
        void completedHasNoTransitions() {
            org.junit.jupiter.api.Assertions.assertFalse(
                    SessionStatus.COMPLETED.canTransitionTo(SessionStatus.IN_PROGRESS));
            org.junit.jupiter.api.Assertions.assertFalse(
                    SessionStatus.COMPLETED.canTransitionTo(SessionStatus.PAUSED));
            org.junit.jupiter.api.Assertions.assertFalse(
                    SessionStatus.COMPLETED.canTransitionTo(SessionStatus.CANCELLED));
        }

        @Test
        void cancelledHasNoTransitions() {
            org.junit.jupiter.api.Assertions.assertFalse(
                    SessionStatus.CANCELLED.canTransitionTo(SessionStatus.IN_PROGRESS));
            org.junit.jupiter.api.Assertions.assertFalse(
                    SessionStatus.CANCELLED.canTransitionTo(SessionStatus.COMPLETED));
        }

        @Test
        void failedHasNoTransitions() {
            org.junit.jupiter.api.Assertions.assertFalse(
                    SessionStatus.FAILED.canTransitionTo(SessionStatus.IN_PROGRESS));
            org.junit.jupiter.api.Assertions.assertFalse(
                    SessionStatus.FAILED.canTransitionTo(SessionStatus.COMPLETED));
        }
    }

    @Nested
    class PlanningTransitions {
        @Test
        void planningToInProgress() {
            org.junit.jupiter.api.Assertions.assertTrue(
                    SessionStatus.PLANNING.canTransitionTo(SessionStatus.IN_PROGRESS));
        }

        @Test
        void planningToFailed() {
            org.junit.jupiter.api.Assertions.assertTrue(
                    SessionStatus.PLANNING.canTransitionTo(SessionStatus.FAILED));
        }

        @Test
        void planningNotToCompleted() {
            org.junit.jupiter.api.Assertions.assertFalse(
                    SessionStatus.PLANNING.canTransitionTo(SessionStatus.COMPLETED));
        }
    }

    @Nested
    class TerminalCheck {
        @Test
        void completedIsTerminal() {
            org.junit.jupiter.api.Assertions.assertTrue(SessionStatus.COMPLETED.isTerminal());
        }

        @Test
        void cancelledIsTerminal() {
            org.junit.jupiter.api.Assertions.assertTrue(SessionStatus.CANCELLED.isTerminal());
        }

        @Test
        void failedIsTerminal() {
            org.junit.jupiter.api.Assertions.assertTrue(SessionStatus.FAILED.isTerminal());
        }

        @Test
        void inProgressIsNotTerminal() {
            org.junit.jupiter.api.Assertions.assertFalse(SessionStatus.IN_PROGRESS.isTerminal());
        }
    }

    @Nested
    class AllowedTransitionsList {
        @Test
        void inProgressHasFiveTargets() {
            var targets = SessionStatus.IN_PROGRESS.allowedTransitions();
            org.junit.jupiter.api.Assertions.assertEquals(5, targets.size());
            org.junit.jupiter.api.Assertions.assertTrue(targets.contains(SessionStatus.PAUSED));
            org.junit.jupiter.api.Assertions.assertTrue(targets.contains(SessionStatus.COMPLETED));
            org.junit.jupiter.api.Assertions.assertTrue(targets.contains(SessionStatus.EVALUATING));
            org.junit.jupiter.api.Assertions.assertTrue(targets.contains(SessionStatus.CANCELLED));
            org.junit.jupiter.api.Assertions.assertTrue(targets.contains(SessionStatus.FAILED));
        }

        @Test
        void terminalReturnsEmptySet() {
            org.junit.jupiter.api.Assertions.assertTrue(
                    SessionStatus.COMPLETED.allowedTransitions().isEmpty());
            org.junit.jupiter.api.Assertions.assertTrue(
                    SessionStatus.CANCELLED.allowedTransitions().isEmpty());
        }

        @Test
        void createdReturnsThreeTargets() {
            var targets = SessionStatus.CREATED.allowedTransitions();
            org.junit.jupiter.api.Assertions.assertEquals(3, targets.size());
        }
    }
}
