import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Forbidden } from './forbidden';

describe('Forbidden', () => {
  it('should tell the user they have no access and link home', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Forbidden);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('h1')?.textContent).toBe("You don't have access");
    expect(el.querySelector('a')?.getAttribute('href')).toBe('/');
  });
});
