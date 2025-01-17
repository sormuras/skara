/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.skara.bots.pr;

import org.openjdk.skara.forge.Review;
import org.openjdk.skara.test.*;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.openjdk.skara.bots.pr.PullRequestAsserts.assertLastCommentContains;

public class ReviewersTests {
    @Test
    void simple(TestInfo testInfo) throws IOException {
        try (var credentials = new HostCredentials(testInfo);
             var tempFolder = new TemporaryDirectory()) {
            var author = credentials.getHostedRepository();
            var integrator = credentials.getHostedRepository();
            var bot = credentials.getHostedRepository();

            var censusBuilder = credentials.getCensusBuilder()
                                           .addReviewer(integrator.forge().currentUser().id())
                                           .addCommitter(author.forge().currentUser().id());
            var prBot = PullRequestBot.newBuilder().repo(bot).censusRepo(censusBuilder.build()).build();

            // Populate the projects repository
            var localRepoFolder = tempFolder.path().resolve("localrepo");
            var localRepo = CheckableRepository.init(localRepoFolder, author.repositoryType());
            var masterHash = localRepo.resolve("master").orElseThrow();
            assertFalse(CheckableRepository.hasBeenEdited(localRepo));
            localRepo.push(masterHash, author.url(), "master", true);

            // Make a change with a corresponding PR
            var editHash = CheckableRepository.appendAndCommit(localRepo);
            localRepo.push(editHash, author.url(), "edit", true);
            var pr = credentials.createPullRequest(author, "master", "edit", "123: This is a pull request");

            var reviewerPr = integrator.pullRequest(pr.id());

            // No arguments
            reviewerPr.addComment("/reviewers");
            TestBotRunner.runPeriodicItems(prBot);

            // The bot should reply with a help message
            assertLastCommentContains(reviewerPr,"is the number of required reviewers");

            // Invalid syntax
            reviewerPr.addComment("/reviewers two");
            TestBotRunner.runPeriodicItems(prBot);

            // The bot should reply with a help message
            assertLastCommentContains(reviewerPr,"is the number of required reviewers");

            // Too many
            reviewerPr.addComment("/reviewers 7001");
            TestBotRunner.runPeriodicItems(prBot);
            assertLastCommentContains(reviewerPr,"Cannot increase the required number of reviewers above 10 (requested: 7001)");

            // Too few
            reviewerPr.addComment("/reviewers -3");
            TestBotRunner.runPeriodicItems(prBot);
            assertLastCommentContains(reviewerPr,"Number of required reviewers of role authors cannot be decreased below 0");

            // Unknown role
            reviewerPr.addComment("/reviewers 2 penguins");
            TestBotRunner.runPeriodicItems(prBot);
            assertLastCommentContains(reviewerPr,"Unknown role `penguins` specified");

            // Set the number
            reviewerPr.addComment("/reviewers 2");
            TestBotRunner.runPeriodicItems(prBot);

            // The bot should reply with a success message
            assertLastCommentContains(reviewerPr,"The number of required reviews for this PR is now set to 2 (with at least 1 of role reviewers).");

            // Approve it as another user
            reviewerPr.addReview(Review.Verdict.APPROVED, "Approved");
            TestBotRunner.runPeriodicItems(prBot);
            TestBotRunner.runPeriodicItems(prBot);

            // The PR should not yet be considered as ready for review
            var updatedPr = author.pullRequest(pr.id());
            assertFalse(updatedPr.labelNames().contains("ready"));

            // Now reduce the number of required reviewers
            reviewerPr.addComment("/reviewers 1");
            TestBotRunner.runPeriodicItems(prBot);
            TestBotRunner.runPeriodicItems(prBot);

            // The PR should now be considered as ready for review
            updatedPr = author.pullRequest(pr.id());
            assertTrue(updatedPr.labelNames().contains("ready"));

            // Now request that the lead reviews
            reviewerPr.addComment("/reviewers 1 lead");
            TestBotRunner.runPeriodicItems(prBot);
            TestBotRunner.runPeriodicItems(prBot);
            assertLastCommentContains(reviewerPr,"The number of required reviews for this PR is now set to 1.");

            // The PR should no longer be considered as ready for review
            updatedPr = author.pullRequest(pr.id());
            assertFalse(updatedPr.labelNames().contains("ready"));

            // Drop the extra requirement that it should be the lead
            reviewerPr.addComment("/reviewers 1");
            TestBotRunner.runPeriodicItems(prBot);
            TestBotRunner.runPeriodicItems(prBot);
            assertLastCommentContains(reviewerPr,"The number of required reviews for this PR is now set to 1.");

            // The PR should now be considered as ready for review yet again
            updatedPr = author.pullRequest(pr.id());
            assertTrue(updatedPr.labelNames().contains("ready"));
        }
    }

    @Test
    void noIntegration(TestInfo testInfo) throws IOException {
        try (var credentials = new HostCredentials(testInfo);
             var tempFolder = new TemporaryDirectory()) {
            var author = credentials.getHostedRepository();
            var integrator = credentials.getHostedRepository();
            var bot = credentials.getHostedRepository();

            var censusBuilder = credentials.getCensusBuilder()
                                           .addReviewer(integrator.forge().currentUser().id())
                                           .addCommitter(author.forge().currentUser().id());
            var prBot = PullRequestBot.newBuilder().repo(bot).censusRepo(censusBuilder.build()).build();

            // Populate the projects repository
            var localRepoFolder = tempFolder.path().resolve("localrepo");
            var localRepo = CheckableRepository.init(localRepoFolder, author.repositoryType());
            var masterHash = localRepo.resolve("master").orElseThrow();
            assertFalse(CheckableRepository.hasBeenEdited(localRepo));
            localRepo.push(masterHash, author.url(), "master", true);

            // Make a change with a corresponding PR
            var editHash = CheckableRepository.appendAndCommit(localRepo);
            localRepo.push(editHash, author.url(), "edit", true);
            var pr = credentials.createPullRequest(author, "master", "edit", "123: This is a pull request");

            var reviewerPr = integrator.pullRequest(pr.id());

            // Set the number
            reviewerPr.addComment("/reviewers 2");
            TestBotRunner.runPeriodicItems(prBot);

            // The bot should reply with a success message
            assertLastCommentContains(reviewerPr,"The number of required reviews for this PR is now set to 2 (with at least 1 of role reviewers).");

            // Approve it as another user
            reviewerPr.addReview(Review.Verdict.APPROVED, "Approved");
            TestBotRunner.runPeriodicItems(prBot);
            TestBotRunner.runPeriodicItems(prBot);

            // It should not be possible to integrate yet
            pr.addComment("/integrate");
            TestBotRunner.runPeriodicItems(prBot);
            assertLastCommentContains(reviewerPr,"pull request has not yet been marked as ready for integration");

            // Relax the requirement
            reviewerPr.addComment("/reviewers 1");
            TestBotRunner.runPeriodicItems(prBot);

            // It should now work fine
            pr.addComment("/integrate");
            TestBotRunner.runPeriodicItems(prBot);
            assertLastCommentContains(reviewerPr,"Pushed as commit");
        }
    }

    @Test
    void noSponsoring(TestInfo testInfo) throws IOException {
        try (var credentials = new HostCredentials(testInfo);
             var tempFolder = new TemporaryDirectory()) {
            var author = credentials.getHostedRepository();
            var integrator = credentials.getHostedRepository();
            var bot = credentials.getHostedRepository();

            var censusBuilder = credentials.getCensusBuilder()
                                           .addReviewer(integrator.forge().currentUser().id())
                                           .addAuthor(author.forge().currentUser().id());
            var prBot = PullRequestBot.newBuilder().repo(bot).censusRepo(censusBuilder.build()).build();

            // Populate the projects repository
            var localRepoFolder = tempFolder.path().resolve("localrepo");
            var localRepo = CheckableRepository.init(localRepoFolder, author.repositoryType());
            var masterHash = localRepo.resolve("master").orElseThrow();
            assertFalse(CheckableRepository.hasBeenEdited(localRepo));
            localRepo.push(masterHash, author.url(), "master", true);

            // Make a change with a corresponding PR
            var editHash = CheckableRepository.appendAndCommit(localRepo);
            localRepo.push(editHash, author.url(), "edit", true);
            var pr = credentials.createPullRequest(author, "master", "edit", "123: This is a pull request");

            var reviewerPr = integrator.pullRequest(pr.id());

            // Approve it as another user
            reviewerPr.addReview(Review.Verdict.APPROVED, "Approved");
            TestBotRunner.runPeriodicItems(prBot);
            TestBotRunner.runPeriodicItems(prBot);

            // Flag it as ready for integration
            pr.addComment("/integrate");
            TestBotRunner.runPeriodicItems(prBot);
            assertLastCommentContains(reviewerPr,"now ready to be sponsored");

            // Set the number
            reviewerPr.addComment("/reviewers 2");
            TestBotRunner.runPeriodicItems(prBot);

            // The bot should reply with a success message
            assertLastCommentContains(reviewerPr,"The number of required reviews for this PR is now set to 2 (with at least 1 of role reviewers).");

            // It should not be possible to sponsor
            reviewerPr.addComment("/sponsor");
            TestBotRunner.runPeriodicItems(prBot);
            assertLastCommentContains(reviewerPr,"PR has not yet been marked as ready for integration");

            // Relax the requirement
            reviewerPr.addComment("/reviewers 1");
            TestBotRunner.runPeriodicItems(prBot);

            // It should now work fine
            reviewerPr.addComment("/sponsor");
            TestBotRunner.runPeriodicItems(prBot);
            assertLastCommentContains(reviewerPr,"Pushed as commit");
        }
    }

    @Test
    void prAuthorShouldBeAllowedToExecute(TestInfo testInfo) throws IOException {
        try (var credentials = new HostCredentials(testInfo);
             var tempFolder = new TemporaryDirectory()) {
            var author = credentials.getHostedRepository();
            var bot = credentials.getHostedRepository();

            var censusBuilder = credentials.getCensusBuilder()
                                           .addCommitter(author.forge().currentUser().id());
            var prBot = PullRequestBot.newBuilder().repo(bot).censusRepo(censusBuilder.build()).build();

            // Populate the projects repository
            var localRepoFolder = tempFolder.path().resolve("localrepo");
            var localRepo = CheckableRepository.init(localRepoFolder, author.repositoryType());
            var masterHash = localRepo.resolve("master").orElseThrow();
            assertFalse(CheckableRepository.hasBeenEdited(localRepo));
            localRepo.push(masterHash, author.url(), "master", true);

            // Make a change with a corresponding PR
            var editHash = CheckableRepository.appendAndCommit(localRepo);
            localRepo.push(editHash, author.url(), "edit", true);
            var pr = credentials.createPullRequest(author, "master", "edit", "123: This is a pull request");

            var authorPR = author.pullRequest(pr.id());

            // The author deems that two reviewers are required
            authorPR.addComment("/reviewers 2");

            // The bot should reply with a success message
            TestBotRunner.runPeriodicItems(prBot);
            assertLastCommentContains(authorPR, "The number of required reviews for this PR is now set to 2 (with at least 1 of role reviewers).");
        }
    }

    @Test
    void prAuthorShouldNotBeAllowedToDecrease(TestInfo testInfo) throws IOException {
        try (var credentials = new HostCredentials(testInfo);
             var tempFolder = new TemporaryDirectory()) {
            var author = credentials.getHostedRepository();
            var integrator = credentials.getHostedRepository();
            var bot = credentials.getHostedRepository();

            var censusBuilder = credentials.getCensusBuilder()
                                           .addReviewer(integrator.forge().currentUser().id())
                                           .addAuthor(author.forge().currentUser().id());
            var prBot = PullRequestBot.newBuilder().repo(bot).censusRepo(censusBuilder.build()).build();

            // Populate the projects repository
            var localRepoFolder = tempFolder.path().resolve("localrepo");
            var localRepo = CheckableRepository.init(localRepoFolder, author.repositoryType());
            var masterHash = localRepo.resolve("master").orElseThrow();
            assertFalse(CheckableRepository.hasBeenEdited(localRepo));
            localRepo.push(masterHash, author.url(), "master", true);

            // Make a change with a corresponding PR
            var editHash = CheckableRepository.appendAndCommit(localRepo);
            localRepo.push(editHash, author.url(), "edit", true);
            var pr = credentials.createPullRequest(author, "master", "edit", "123: This is a pull request");

            var authorPR = author.pullRequest(pr.id());

            // The author deems that two reviewers are required
            authorPR.addComment("/reviewers 2");

            // The bot should reply with a success message
            TestBotRunner.runPeriodicItems(prBot);
            assertLastCommentContains(authorPR, "The number of required reviews for this PR is now set to 2 (with at least 1 of role reviewers).");

            // The author should not be allowed to decrease even its own /reviewers command
            authorPR.addComment("/reviewers 1");
            TestBotRunner.runPeriodicItems(prBot);
            assertLastCommentContains(authorPR, "Cannot decrease the number of required reviewers");

            // Reviewer should be allowed to decrease
            var reviewerPr = integrator.pullRequest(pr.id());
            reviewerPr.addComment("/reviewers 1");
            TestBotRunner.runPeriodicItems(prBot);
            assertLastCommentContains(authorPR, "The number of required reviews for this PR is now set to 1");
        }
    }

    @Test
    void commandInPRBody(TestInfo testInfo) throws IOException {
        try (var credentials = new HostCredentials(testInfo);
             var tempFolder = new TemporaryDirectory()) {
            var author = credentials.getHostedRepository();
            var bot = credentials.getHostedRepository();

            var censusBuilder = credentials.getCensusBuilder()
                                           .addCommitter(author.forge().currentUser().id());
            var prBot = PullRequestBot.newBuilder().repo(bot).censusRepo(censusBuilder.build()).build();

            // Populate the projects repository
            var localRepoFolder = tempFolder.path().resolve("localrepo");
            var localRepo = CheckableRepository.init(localRepoFolder, author.repositoryType());
            var masterHash = localRepo.resolve("master").orElseThrow();
            assertFalse(CheckableRepository.hasBeenEdited(localRepo));
            localRepo.push(masterHash, author.url(), "master", true);

            // Make a change with a corresponding PR
            var editHash = CheckableRepository.appendAndCommit(localRepo);
            localRepo.push(editHash, author.url(), "edit", true);
            var pr = credentials.createPullRequest(author, "master", "edit", "123: This is a pull request", List.of("/reviewers 2"));

            TestBotRunner.runPeriodicItems(prBot);

            var authorPR = author.pullRequest(pr.id());
            assertLastCommentContains(authorPR,"The number of required reviews for this PR is now set to 2 (with at least 1 of role reviewers).");
        }
    }
}
