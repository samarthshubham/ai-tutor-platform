export interface GuardianLink {
  id: string;
  parentId: string;
  studentId: string;
}

export interface GuardianLinkRequest {
  parentId: string;
  studentId: string;
}

export interface GuardianLinkQuery {
  parentId?: string;
  studentId?: string;
  page?: number;
  size?: number;
}

export interface TutorAssignment {
  id: string;
  tutorId: string;
  studentId: string;
}

export interface TutorAssignmentRequest {
  tutorId: string;
  studentId: string;
}

export interface TutorAssignmentQuery {
  tutorId?: string;
  studentId?: string;
  page?: number;
  size?: number;
}
