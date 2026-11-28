import { describe, expect, it, vi } from 'vitest';
import type { SeedApi } from './api-client';
import { COURSES, DemoSeed } from './demo-seed';

const expectedCounts = {
  users: 9,
  guardianLinks: 2,
  tutorAssignments: 2,
  courses: 2,
  topics: 5,
  enrollments: 5,
  lessons: 5,
  published: 4,
  inReview: 1,
};

function fakeApi() {
  const byPath = new Map<string, unknown[]>();
  const lookup = vi.fn(
    async <T>(_user: string, path: string, matches: (item: T) => boolean) =>
      (byPath.get(path) as T[] | undefined)?.find(matches),
  );
  const get = vi.fn(async <T>(user: string, path: string): Promise<T> => {
    if (path === '/me') return { id: user } as T;
    if (path.endsWith('/students')) return (byPath.get(path) ?? []) as T;
    return { content: byPath.get(path) ?? [] } as T;
  });
  const add = (path: string, value: unknown) =>
    byPath.set(path, [...(byPath.get(path) ?? []), value]);
  const post = vi.fn(
    async <T>(
      user: string,
      path: string,
      body?: Record<string, unknown>,
    ): Promise<T> => {
      if (path === '/courses') {
        const course = {
          id: user,
          title: body?.['title'],
          topics: [],
          status: 'ACTIVE',
        };
        add(path, course);
        return course as T;
      }
      const parts = path.split('/');
      if (path.endsWith('/topics')) return { id: body?.['name'], ...body } as T;
      if (path.endsWith('/enrollments')) {
        add(`/courses/${parts[2]}/students`, { id: body?.['studentId'] });
        return {} as T;
      }
      if (path.endsWith('/lessons')) {
        const lesson = {
          id: body?.['title'],
          title: body?.['title'],
          status: 'DRAFT',
        };
        add(path, lesson);
        return lesson as T;
      }
      if (path.startsWith('/lessons/')) {
        const lessons = [...byPath.values()].flat() as {
          id: string;
          status: string;
        }[];
        const lesson = lessons.find(({ id }) => id === parts[2]);
        if (!lesson) throw new Error('Unexpected lesson route: ' + path);
        lesson.status = parts[3] === 'submit' ? 'IN_REVIEW' : 'PUBLISHED';
        return lesson as T;
      }
      add(`${path}?${new URLSearchParams(body as Record<string, string>)}`, {
        id: 'relationship',
      });
      return {} as T;
    },
  );
  return {
    checkServices: vi.fn(async () => undefined),
    get: get as SeedApi['get'] & typeof get,
    post: post as SeedApi['post'] & typeof post,
    find: lookup as SeedApi['find'] & typeof lookup,
  };
}

async function runSeed(seed: DemoSeed) {
  await seed.signIn();
  await seed.linkStudents();
  await seed.prepareCourses();
  await seed.prepareLessons();
}

describe('DemoSeed', () => {
  it('creates the demo records once and uses the teacher and admin for workflow transitions', async () => {
    const api = fakeApi();
    const content = vi.fn(async (name: string) => `# ${name}`);
    const first = new DemoSeed(api, content);
    await runSeed(first);

    expect(first.counts).toEqual(expectedCounts);
    expect(content).toHaveBeenCalledTimes(5);
    expect(api.post).toHaveBeenCalledWith(
      'teacher1',
      '/lessons/Who eats whom/submit',
    );
    expect(api.post).not.toHaveBeenCalledWith(
      'admin1',
      '/lessons/Who eats whom/publish',
    );
    expect(api.post).toHaveBeenCalledWith(
      'admin1',
      '/lessons/How plants make food/publish',
    );
    expect(api.post).not.toHaveBeenCalledWith(
      'teacher2',
      '/courses/teacher2/enrollments',
      { studentId: 'student3' },
    );

    api.post.mockClear();
    const second = new DemoSeed(api, content);
    await runSeed(second);

    expect(second.counts).toEqual(first.counts);
    expect(api.post).not.toHaveBeenCalled();
    expect(content).toHaveBeenCalledTimes(
      COURSES.flatMap(({ lessons }) => [...lessons]).length,
    );
  });

  it('resumes an existing draft and a lesson already in the review queue', async () => {
    const api = fakeApi();
    const seed = new DemoSeed(api, async () => '# Lesson');
    await seed.signIn();
    await seed.prepareCourses();
    await api.post('teacher1', '/courses/teacher1/lessons', {
      title: 'How plants make food',
    });
    await api.post('teacher1', '/courses/teacher1/lessons', {
      title: 'How cells release energy',
    });
    await api.post('teacher1', '/lessons/How cells release energy/submit');
    api.post.mockClear();

    await seed.prepareLessons();

    expect(api.post).toHaveBeenCalledWith(
      'teacher1',
      '/lessons/How plants make food/submit',
    );
    expect(api.post).not.toHaveBeenCalledWith(
      'teacher1',
      '/lessons/How cells release energy/submit',
    );
    expect(api.post).toHaveBeenCalledWith(
      'admin1',
      '/lessons/How cells release energy/publish',
    );
  });

  it('rejects an archived course and a lesson whose status cannot be reconciled', async () => {
    const api = fakeApi();
    const seed = new DemoSeed(api, async () => '# Lesson');
    await seed.signIn();
    await expect(seed.prepareLessons()).rejects.toThrow('must be prepared');
    const course = await api.post<{ status: string }>('teacher1', '/courses', {
      title: 'Grade 8 Science',
    });
    course.status = 'ARCHIVED';
    await expect(seed.prepareCourses()).rejects.toThrow('is archived');
    course.status = 'ACTIVE';

    await seed.prepareCourses();
    const lesson = await api.post<{ status: string }>(
      'teacher1',
      '/courses/teacher1/lessons',
      { title: 'How plants make food' },
    );
    lesson.status = 'ARCHIVED';
    await expect(seed.prepareLessons()).rejects.toThrow('has status ARCHIVED');
  });
});
