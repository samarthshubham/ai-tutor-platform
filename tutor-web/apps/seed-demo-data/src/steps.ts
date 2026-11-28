import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import {
  Given,
  Then,
  When,
  World,
  setDefaultTimeout,
  setWorldConstructor,
} from '@cucumber/cucumber';
import { SeedApi, type Page } from './api-client';
import { DemoSeed } from './demo-seed';

class SeedWorld extends World {
  readonly api = new SeedApi();
  readonly seed = new DemoSeed(this.api, (name) =>
    readFile(resolve('apps/seed-demo-data/src/content', `${name}.md`), 'utf8'),
  );
}

setWorldConstructor(SeedWorld);
setDefaultTimeout(120_000);
Given('the nine demo users have signed in', async function (this: SeedWorld) {
  await this.seed.signIn();
});
Given(
  'the demo parents and tutor are linked to their students',
  async function (this: SeedWorld) {
    await this.seed.linkStudents();
  },
);
Given(
  'the two teachers have their courses, topics and enrolled students',
  async function (this: SeedWorld) {
    await this.seed.prepareCourses();
  },
);
When(
  'the teachers and administrator prepare the five lessons',
  async function (this: SeedWorld) {
    await this.seed.prepareLessons();
  },
);
Then('the seed prints the demo school counts', function (this: SeedWorld) {
  console.table(this.seed.counts);
  console.log(`Seed writes: ${this.api.writeCount}`);
  console.log(`Seed counts: ${JSON.stringify(this.seed.counts)}`);
});

Then(
  'the persisted demo course and lesson counts match the roster',
  async function (this: SeedWorld) {
    type Course = { id: string; title: string; topics: unknown[] };
    type Lesson = { status: string; publishedVersion: number | null };
    const courses = await this.api.get<Page<Course>>('admin1', '/courses');
    assert.equal(courses.totalElements, 2, 'Persisted courses');
    assert.equal(
      courses.content.flatMap(({ topics }) => topics).length,
      5,
      'Persisted topics',
    );

    let enrollments = 0;
    const lessons: Lesson[] = [];
    for (const { id } of courses.content) {
      const students = await this.api.get<unknown[]>(
        'admin1',
        `/courses/${id}/students`,
      );
      enrollments += students.length;
      const page = await this.api.get<Page<Lesson>>(
        'admin1',
        `/courses/${id}/lessons`,
      );
      assert.equal(
        page.content.length,
        page.totalElements,
        'All course lessons fit in one page',
      );
      lessons.push(...page.content);
    }
    assert.equal(enrollments, 5, 'Persisted enrollments');
    assert.equal(lessons.length, 5, 'Persisted lessons');
    assert.equal(
      lessons.filter(
        ({ status, publishedVersion }) =>
          status === 'PUBLISHED' && publishedVersion === 1,
      ).length,
      4,
      'First-version published lessons',
    );
    assert.equal(
      lessons.filter(({ status }) => status === 'IN_REVIEW').length,
      1,
      'Lessons in review',
    );
  },
);

Then('student3 sees only Grade 8 Science', async function (this: SeedWorld) {
  const courses = await this.api.get<Page<{ title: string }>>(
    'student3',
    '/courses',
  );
  assert.equal(courses.totalElements, 1, 'Courses visible to student3');
  assert.deepEqual(
    courses.content.map(({ title }) => title),
    ['Grade 8 Science'],
  );
});

Then(
  'parent1 and tutor1 see their linked students',
  async function (this: SeedWorld) {
    type Person = { id: string };
    const [sam, sara, parentStudents, tutorStudents] = await Promise.all([
      this.api.get<Person>('student1', '/me'),
      this.api.get<Person>('student2', '/me'),
      this.api.get<Person[]>('parent1', '/me/students'),
      this.api.get<Person[]>('tutor1', '/me/students'),
    ]);
    assert.deepEqual(
      parentStudents.map(({ id }) => id),
      [sam.id],
      'Students visible to parent1',
    );
    assert.deepEqual(
      tutorStudents.map(({ id }) => id).sort(),
      [sam.id, sara.id].sort(),
      'Students visible to tutor1',
    );
  },
);
