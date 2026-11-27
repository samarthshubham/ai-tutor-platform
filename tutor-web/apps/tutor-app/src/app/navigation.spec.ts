import { Route } from '@angular/router';
import { ROLES } from '@tutor-web/api';
import { HOME_ITEM, navigationFor, ROLE_NAVIGATION } from './navigation';

describe('navigationFor', () => {
  const routes = (...paths: string[]): Route[] =>
    paths.map((path) => ({ path }));

  it('should list the role items whose route exists, in the design order', () => {
    expect(
      navigationFor('ADMIN', routes('admin/users', 'courses', 'home')).map(
        (item) => item.label,
      ),
    ).toEqual(['Users', 'Courses']);
  });

  it.each(ROLES)(
    'should fall back to Home for %s while no route of the role exists',
    (role) => {
      expect(navigationFor(role, routes('home', 'forbidden'))).toEqual([
        HOME_ITEM,
      ]);
    },
  );

  it('should show a role only its own items', () => {
    const everything = Object.values(ROLE_NAVIGATION)
      .flat()
      .map((item) => item.path.slice(1));

    expect(
      navigationFor('PARENT', routes(...everything)).map((item) => item.label),
    ).toEqual(['My children']);
    expect(
      navigationFor('STUDENT', routes(...everything)).map((item) => item.label),
    ).toEqual(['My courses', 'Practice', 'Progress', 'Tutor chat']);
  });
});
