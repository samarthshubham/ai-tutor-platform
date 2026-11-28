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
import { SeedApi } from './api-client';
import { DemoSeed } from './demo-seed';

class SeedWorld extends World {
  readonly seed = new DemoSeed(new SeedApi(), (name) =>
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
  console.log(`Seed counts: ${JSON.stringify(this.seed.counts)}`);
});
