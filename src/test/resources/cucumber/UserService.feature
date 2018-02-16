Feature: UserService API Services
  This feature is for example purpose

#@Ignored
  Scenario: A permitted user can add a user and list it after
    Given a logged in and permitted user
    When he adds a new user
    Then he will receive a list of users
    Then he can be received by id

  Scenario: A not permitted user can not add a new user
    Given a not authorized user
    When he tries to add a user
    Then the new user is not there