import { DEMO_USERS, type DemoUser } from '@tutor-web/e2e-support';
import type { SeedApi } from './api-client';

type Teacher = 'teacher1' | 'teacher2';
type Student = 'student1' | 'student2' | 'student3';
type LessonStatus = 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'ARCHIVED';
interface Entity {
  id: string;
}
interface Topic extends Entity {
  name: string;
}
interface Course extends Entity {
  title: string;
  topics: Topic[];
  status: 'ACTIVE' | 'ARCHIVED';
}
interface Lesson extends Entity {
  title: string;
  status: LessonStatus;
}
interface LessonPlan {
  title: string;
  topic: string;
  content: string;
  status: 'PUBLISHED' | 'IN_REVIEW';
}

export const COURSES = [
  {
    teacher: 'teacher1',
    title: 'Grade 8 Science',
    subject: 'Science',
    topics: ['Photosynthesis', 'Respiration', 'Food chains'],
    students: ['student1', 'student2', 'student3'],
    lessons: [
      {
        title: 'How plants make food',
        topic: 'Photosynthesis',
        content: 'photosynthesis',
        status: 'PUBLISHED',
      },
      {
        title: 'How cells release energy',
        topic: 'Respiration',
        content: 'respiration',
        status: 'PUBLISHED',
      },
      {
        title: 'Who eats whom',
        topic: 'Food chains',
        content: 'food-chains',
        status: 'IN_REVIEW',
      },
    ],
  },
  {
    teacher: 'teacher2',
    title: 'Grade 8 English Writing',
    subject: 'English Writing',
    topics: ['Paragraph structure', 'Persuasive writing'],
    students: ['student1', 'student2'],
    lessons: [
      {
        title: 'Building a paragraph',
        topic: 'Paragraph structure',
        content: 'paragraphs',
        status: 'PUBLISHED',
      },
      {
        title: 'Writing to persuade',
        topic: 'Persuasive writing',
        content: 'persuasion',
        status: 'PUBLISHED',
      },
    ],
  },
] as const satisfies readonly {
  teacher: Teacher;
  title: string;
  subject: string;
  topics: readonly string[];
  students: readonly Student[];
  lessons: readonly LessonPlan[];
}[];

export class DemoSeed {
  readonly counts = {
    users: 0,
    guardianLinks: 0,
    tutorAssignments: 0,
    courses: 0,
    topics: 0,
    enrollments: 0,
    lessons: 0,
    published: 0,
    inReview: 0,
  };
  private readonly users = new Map<DemoUser, string>();
  private readonly courses = new Map<Teacher, Course>();

  constructor(
    private readonly api: Pick<
      SeedApi,
      'checkServices' | 'get' | 'post' | 'find'
    >,
    private readonly readContent: (name: string) => Promise<string>,
  ) {}

  async signIn(): Promise<void> {
    await this.api.checkServices();
    for (const user of Object.keys(DEMO_USERS) as DemoUser[]) {
      const { id } = await this.api.get<Entity>(user, '/me');
      this.users.set(user, id);
      this.counts.users++;
    }
  }

  async linkStudents(): Promise<void> {
    for (const [parent, student] of [
      ['parent1', 'student1'],
      ['parent2', 'student2'],
    ] as const) {
      await this.ensureLink('/guardian-links', 'parentId', parent, student);
      this.counts.guardianLinks++;
    }
    for (const student of ['student1', 'student2'] as const) {
      await this.ensureLink('/tutor-assignments', 'tutorId', 'tutor1', student);
      this.counts.tutorAssignments++;
    }
  }

  async prepareCourses(): Promise<void> {
    for (const { teacher, title, subject, topics, students } of COURSES) {
      const existing = await this.api.find<Course>(
        teacher,
        '/courses',
        (course) => course.title === title,
      );
      const course =
        existing ??
        (await this.api.post<Course>(teacher, '/courses', {
          title,
          subject,
          gradeLevel: 8,
        }));
      if (course.status === 'ARCHIVED') {
        throw new Error(
          `Demo course "${title}" is archived. Use an active demo course.`,
        );
      }
      this.counts.courses++;
      const path = `/courses/${course.id}`;
      for (const [index, name] of topics.entries()) {
        if (!course.topics.some((topic) => topic.name === name)) {
          course.topics.push(
            await this.api.post<Topic>(teacher, `${path}/topics`, {
              name,
              position: index + 1,
            }),
          );
        }
        this.counts.topics++;
      }
      this.courses.set(teacher, course);
      const enrolled = await this.api.get<Entity[]>(
        teacher,
        `${path}/students`,
      );
      for (const student of students) {
        const studentId = this.users.get(student);
        if (!enrolled.some(({ id }) => id === studentId)) {
          await this.api.post(teacher, `${path}/enrollments`, { studentId });
        }
        this.counts.enrollments++;
      }
    }
  }

  async prepareLessons(): Promise<void> {
    for (const { teacher, lessons } of COURSES) {
      const course = this.courses.get(teacher);
      if (!course) {
        throw new Error(
          `Course for ${teacher} must be prepared before its lessons.`,
        );
      }
      for (const lesson of lessons) {
        await this.ensureLesson(teacher, course, lesson);
        this.counts.lessons++;
        this.counts[lesson.status === 'PUBLISHED' ? 'published' : 'inReview']++;
      }
    }
  }

  private async ensureLink(
    path: string,
    field: string,
    user: DemoUser,
    student: Student,
  ): Promise<void> {
    const userId = this.users.get(user);
    const studentId = this.users.get(student);
    const query = new URLSearchParams({
      [field]: String(userId),
      studentId: String(studentId),
    });
    const existing = await this.api.get<{ content: Entity[] }>(
      'admin1',
      `${path}?${query}`,
    );
    if (existing.content.length === 0) {
      await this.api.post('admin1', path, { [field]: userId, studentId });
    }
  }

  private async ensureLesson(
    teacher: Teacher,
    course: Course,
    plan: LessonPlan,
  ): Promise<void> {
    const path = `/courses/${course.id}/lessons`;
    const existing = await this.api.find<Lesson>(
      teacher,
      path,
      (lesson) => lesson.title === plan.title,
    );
    let lesson =
      existing ??
      (await this.api.post<Lesson>(teacher, path, {
        title: plan.title,
        topicId: course.topics.find(({ name }) => name === plan.topic)?.id,
        bodyMarkdown: await this.readContent(plan.content),
      }));
    const lessonPath = `/lessons/${lesson.id}`;
    if (lesson.status === 'DRAFT') {
      lesson = await this.api.post(teacher, `${lessonPath}/submit`);
    }
    if (lesson.status === 'IN_REVIEW' && plan.status === 'PUBLISHED') {
      lesson = await this.api.post('admin1', `${lessonPath}/publish`);
    }
    if (lesson.status !== plan.status) {
      throw new Error(
        `Demo lesson "${plan.title}" has status ${lesson.status}, expected ${plan.status}.`,
      );
    }
  }
}
